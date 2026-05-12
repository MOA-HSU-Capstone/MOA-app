"""
meeting_service.py

회의 관련 비즈니스 로직을 담당하는 서비스 계층

역할
- 회의 생성
- 로그인 사용자 기준 회의 목록 조회
- 로그인 사용자 기준 회의 단건 조회
- 로그인 사용자 기준 회의 수정
- 로그인 사용자 기준 회의 삭제
- STT JSON + OCR JSON 기반 summary 생성
- 회의의 summary 조회
- 회의의 전체 transcript(전문) 조회

가정
- 현재 프로젝트에서는 회의당 summary를 1개로 관리
- summary 재생성 시 기존 summary를 삭제하고 새로 저장
- summary는 DB에는 JSON 문자열로 저장하고,
  API 응답에서는 dict 형태로 반환
- transcript와 image(ocr/analysis)는 모두 meeting_id를 기준으로 연결됨
"""

from __future__ import annotations

import json
from typing import Any

from sqlalchemy.orm import Session

from ai.meeting_summarizer import summarize_meeting_from_payload
from models.user_model import User
from repositories.image_repository import get_images_by_meeting_id
from repositories.meeting_repository import (
    create_meeting,
    delete_meeting,
    get_meeting_by_id_and_user_id,
    get_meetings_by_user_id,
    update_meeting,
)
from repositories.summary_repository import (
    create_summary,
    delete_summary,
    get_summary_by_meeting_id,
)
from repositories.transcript_repository import get_transcripts_by_meeting_id
from schemas.meeting_schema import (
    MeetingCreate,
    MeetingResponse,
    MeetingUpdate,
)
from schemas.summary_schema import (
    SummaryCreate,
    SummaryDetailResponse,
    SummaryGenerateResponse,
)


# -----------------------------------------
# 참석자 변환 유틸
# -----------------------------------------

def attendees_list_to_text(attendees: list[str] | None) -> str | None:
    """
    API 요청에서 받은 참석자 list를 DB 저장용 문자열로 변환

    예시
    ----
    ["홍길동", "김철수"] -> "홍길동,김철수"
    """

    if not attendees:
        return None

    cleaned_attendees = [
        attendee.strip()
        for attendee in attendees
        if attendee and attendee.strip()
    ]

    if not cleaned_attendees:
        return None

    return ",".join(cleaned_attendees)


def attendees_text_to_list(attendees_text: str | None) -> list[str]:
    """
    DB에 저장된 참석자 문자열을 API 응답용 list로 변환

    예시
    ----
    "홍길동,김철수" -> ["홍길동", "김철수"]
    """

    if not attendees_text:
        return []

    return [
        attendee.strip()
        for attendee in attendees_text.split(",")
        if attendee.strip()
    ]


def meeting_to_response(meeting) -> MeetingResponse:
    """
    Meeting ORM 객체를 MeetingResponse로 변환

    이유
    ----
    DB에는 attendees가 문자열로 저장되지만,
    API 응답에서는 list[str]로 반환해야 하기 때문이다.
    """

    return MeetingResponse(
        id=meeting.id,
        title=meeting.title,
        meeting_date=getattr(meeting, "meeting_date", None),
        meeting_time=getattr(meeting, "meeting_time", None),
        attendees=attendees_text_to_list(getattr(meeting, "attendees", None)),
        description=getattr(meeting, "description", None),
        created_at=meeting.created_at,
        updated_at=meeting.updated_at,
    )


# -----------------------------------------
# 회의 기본 CRUD
# -----------------------------------------

def create_new_meeting(
    db: Session,
    meeting_data: MeetingCreate,
    current_user: User,
) -> MeetingResponse:
    """
    회의 생성 서비스

    현재 로그인한 사용자의 id를 user_id로 저장한다.
    """

    attendees_text = attendees_list_to_text(meeting_data.attendees)

    meeting = create_meeting(
        db=db,
        meeting_data=meeting_data,
        user_id=current_user.id,
        attendees_text=attendees_text,
    )

    return meeting_to_response(meeting)


def get_meeting_detail(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> MeetingResponse | None:
    """
    현재 로그인한 사용자의 회의 단건 조회 서비스
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return None

    return meeting_to_response(meeting)


def get_meeting_list(
    db: Session,
    current_user: User,
    skip: int = 0,
    limit: int = 100,
) -> list[MeetingResponse]:
    """
    현재 로그인한 사용자의 회의 목록 조회 서비스
    """

    meetings = get_meetings_by_user_id(
        db=db,
        user_id=current_user.id,
        skip=skip,
        limit=limit,
    )

    return [
        meeting_to_response(meeting)
        for meeting in meetings
    ]


def update_meeting_detail(
    db: Session,
    meeting_id: int,
    meeting_data: MeetingUpdate,
    current_user: User,
) -> MeetingResponse | None:
    """
    현재 로그인한 사용자의 회의 수정 서비스
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return None

    attendees_text = None

    if meeting_data.attendees is not None:
        attendees_text = attendees_list_to_text(meeting_data.attendees)

    updated_meeting = update_meeting(
        db=db,
        meeting=meeting,
        meeting_data=meeting_data,
        attendees_text=attendees_text,
    )

    return meeting_to_response(updated_meeting)


def remove_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> bool:
    """
    현재 로그인한 사용자의 회의 삭제 서비스

    Returns
    -------
    bool
        삭제 성공 여부
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return False

    delete_meeting(db, meeting)
    return True


def get_full_transcript_for_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> str | None:
    """
    특정 회의의 전체 transcript(전문)를 하나의 문자열로 반환

    동작 방식
    --------
    1. 현재 로그인한 사용자의 회의인지 확인
    2. 해당 회의의 transcript 전체 조회
    3. 시간 순서대로 transcript 내용을 이어 붙여 하나의 문자열 생성

    Returns
    -------
    str | None
        회의가 없으면 None
        transcript가 없으면 빈 문자열("")
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return None

    transcripts = get_transcripts_by_meeting_id(db, meeting_id)

    if not transcripts:
        return ""

    full_text = "\n".join(
        transcript.content
        for transcript in reversed(transcripts)
        if (transcript.content or "").strip()
    )

    return full_text


# -----------------------------------------
# Summary 생성용 payload 변환
# -----------------------------------------

def _build_stt_payload(transcripts: list[Any]) -> list[dict[str, Any]]:
    """
    transcript ORM 목록을 LLM 입력용 STT JSON 배열로 변환
    """

    stt_items: list[dict[str, Any]] = []

    for transcript in reversed(transcripts):
        content = (transcript.content or "").strip()

        if not content:
            continue

        stt_items.append(
            {
                "id": transcript.id,
                "meeting_id": transcript.meeting_id,
                "content": content,
                "created_at": (
                    transcript.created_at.isoformat()
                    if getattr(transcript, "created_at", None)
                    else None
                ),
            }
        )

    return stt_items


def _build_ocr_payload(images: list[Any]) -> list[dict[str, Any]]:
    """
    image ORM 목록을 LLM 입력용 OCR JSON 배열로 변환
    """

    ocr_items: list[dict[str, Any]] = []

    for image in reversed(images):
        ocr_text = (getattr(image, "ocr_text", "") or "").strip()
        analysis_text = (getattr(image, "analysis_text", "") or "").strip()

        if not ocr_text and not analysis_text:
            continue

        ocr_items.append(
            {
                "id": image.id,
                "meeting_id": image.meeting_id,
                "file_path": image.file_path,
                "image_type": getattr(image, "image_type", None),
                "ocr_text": ocr_text,
                "analysis_text": analysis_text,
                "created_at": (
                    image.created_at.isoformat()
                    if getattr(image, "created_at", None)
                    else None
                ),
            }
        )

    return ocr_items


# -----------------------------------------
# Summary
# -----------------------------------------

def create_summary_for_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> SummaryGenerateResponse | None:
    """
    특정 회의의 STT JSON + OCR JSON을 기반으로 summary를 생성하고 저장
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return None

    transcripts = get_transcripts_by_meeting_id(db, meeting_id)
    images = get_images_by_meeting_id(db, meeting_id)

    stt_items = _build_stt_payload(transcripts)
    ocr_items = _build_ocr_payload(images)

    if not stt_items and not ocr_items:
        return None

    llm_payload = {
        "meeting": {
            "meeting_id": meeting.id,
            "title": meeting.title,
            "meeting_date": getattr(meeting, "meeting_date", None),
            "meeting_time": getattr(meeting, "meeting_time", None),
            "attendees": attendees_text_to_list(getattr(meeting, "attendees", None)),
            "description": getattr(meeting, "description", None),
        },
        "stt": stt_items,
        "ocr": ocr_items,
    }

    existing_summary = get_summary_by_meeting_id(db, meeting_id)

    if existing_summary is not None:
        delete_summary(db, existing_summary)

    summary_result = summarize_meeting_from_payload(llm_payload)

    summary_result.setdefault("summary", "")
    summary_result.setdefault("decisions", [])
    summary_result.setdefault("action_items", [])

    summary_content = json.dumps(summary_result, ensure_ascii=False)

    summary_data = SummaryCreate(
        meeting_id=meeting_id,
        content=summary_content,
    )

    create_summary(db, summary_data)

    return SummaryGenerateResponse(
        meeting_id=meeting_id,
        summary=summary_result,
    )


def get_summary_for_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> SummaryDetailResponse | None:
    """
    특정 회의의 summary 조회 서비스
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return None

    summary = get_summary_by_meeting_id(db, meeting_id)

    if summary is None:
        return None

    try:
        parsed_summary = json.loads(summary.content)
    except json.JSONDecodeError:
        parsed_summary = {
            "summary": summary.content,
            "decisions": [],
            "action_items": [],
        }

    parsed_summary.setdefault("summary", "")
    parsed_summary.setdefault("decisions", [])
    parsed_summary.setdefault("action_items", [])

    return SummaryDetailResponse(
        id=summary.id,
        meeting_id=summary.meeting_id,
        summary=parsed_summary,
        created_at=summary.created_at,
        updated_at=summary.updated_at,
    )