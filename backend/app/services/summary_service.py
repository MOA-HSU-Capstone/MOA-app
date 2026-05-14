"""
summary_service.py

회의 요약 관련 비즈니스 로직을 담당하는 서비스 계층

역할
- STT JSON + OCR JSON 기반 summary 생성
- LLM 요약 결과를 summaries / decisions / action_items 테이블로 나누어 저장
- 회의의 summary 조회
- summary 본문 수정

현재 구조
- summaries 테이블: 회의 요약 본문 저장
- decisions 테이블: 결정사항 목록 저장
- action_items 테이블: 할 일 목록 저장

주의
- 결정사항 하나 추가/수정/삭제는 decision_service.py에서 담당한다.
- 할 일 하나 추가/수정/삭제는 action_item_service.py에서 담당한다.
- summary 재생성 시 기존 decisions/action_items는 삭제 후 새로 저장한다.
"""

from __future__ import annotations

from typing import Any

from sqlalchemy.orm import Session

from ai.meeting_summarizer import summarize_meeting_from_payload
from models.user_model import User
from repositories.action_item_repository import (
    create_action_item,
    delete_action_items_by_meeting_id,
    get_action_items_by_meeting_id,
)
from repositories.decision_repository import (
    create_decision,
    delete_decisions_by_meeting_id,
    get_decisions_by_meeting_id,
)
from repositories.image_repository import get_images_by_meeting_id
from repositories.meeting_repository import get_meeting_by_id_and_user_id
from repositories.summary_repository import (
    get_summary_by_meeting_id,
    update_summary,
    upsert_summary,
)
from repositories.transcript_repository import get_transcripts_by_meeting_id
from schemas.summary_schema import (
    SummaryCreate,
    SummaryDetailResponse,
    SummaryGenerateResponse,
    SummaryUpdateRequest,
)
from services.meeting_service import attendees_text_to_list


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
# Summary 결과 정리 유틸
# -----------------------------------------

def _normalize_summary_result(summary_result: dict[str, Any]) -> dict[str, Any]:
    """
    LLM 결과를 안전한 기본 구조로 정리

    기대 형식
    {
        "summary": "회의 요약 본문",
        "decisions": ["결정사항 1"],
        "action_items": [
            {
                "task": "할 일",
                "assignee": "담당자",
                "due_date": "마감일"
            }
        ]
    }
    """

    summary_text = summary_result.get("summary", "")
    decisions = summary_result.get("decisions", [])
    action_items = summary_result.get("action_items", [])

    if not isinstance(summary_text, str):
        summary_text = str(summary_text)

    if not isinstance(decisions, list):
        decisions = []

    if not isinstance(action_items, list):
        action_items = []

    return {
        "summary": summary_text,
        "decisions": decisions,
        "action_items": action_items,
    }


def _summary_to_detail_response(
    summary,
    decisions,
    action_items,
) -> SummaryDetailResponse:
    """
    Summary ORM + Decision 목록 + ActionItem 목록을 응답 스키마로 변환
    """

    return SummaryDetailResponse(
        id=summary.id,
        meeting_id=summary.meeting_id,
        summary=summary.content,
        decisions=decisions,
        action_items=action_items,
        created_at=summary.created_at,
        updated_at=summary.updated_at,
    )


def _get_summary_with_children(
    db: Session,
    meeting_id: int,
) -> SummaryDetailResponse | None:
    """
    summary + decisions + action_items를 한 번에 조회해서 응답 형태로 변환
    """

    summary = get_summary_by_meeting_id(
        db=db,
        meeting_id=meeting_id,
    )

    if summary is None:
        return None

    decisions = get_decisions_by_meeting_id(
        db=db,
        meeting_id=meeting_id,
    )

    action_items = get_action_items_by_meeting_id(
        db=db,
        meeting_id=meeting_id,
    )

    return _summary_to_detail_response(
        summary=summary,
        decisions=decisions,
        action_items=action_items,
    )


# -----------------------------------------
# Summary 생성 / 조회 / 수정
# -----------------------------------------

def create_summary_for_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> SummaryGenerateResponse | None:
    """
    특정 회의의 STT JSON + OCR JSON을 기반으로 summary를 생성하고 저장

    동작 방식
    --------
    1. 현재 로그인한 사용자의 회의인지 확인
    2. transcript / image OCR 데이터 조회
    3. LLM 요약 생성
    4. summaries 테이블에 요약 본문 upsert
    5. 기존 decisions / action_items는 삭제 후 새로 저장
    6. 응답은 summary, decisions, action_items를 구조화해서 반환
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

    raw_summary_result = summarize_meeting_from_payload(llm_payload)
    summary_result = _normalize_summary_result(raw_summary_result)

    summary_text = summary_result["summary"]
    decisions = summary_result["decisions"]
    action_items = summary_result["action_items"]

    summary_data = SummaryCreate(
        meeting_id=meeting_id,
        content=summary_text,
    )

    summary = upsert_summary(
        db=db,
        summary_data=summary_data,
    )

    # summary를 새로 생성하든 갱신하든,
    # LLM 결과 기준으로 decisions/action_items는 다시 맞춘다.
    delete_decisions_by_meeting_id(
        db=db,
        meeting_id=meeting_id,
    )

    delete_action_items_by_meeting_id(
        db=db,
        meeting_id=meeting_id,
    )

    saved_decisions = []
    saved_action_items = []

    for decision in decisions:
        if not isinstance(decision, str):
            continue

        content = decision.strip()

        if not content:
            continue

        saved_decision = create_decision(
            db=db,
            meeting_id=meeting_id,
            content=content,
        )

        saved_decisions.append(saved_decision)

    for item in action_items:
        if not isinstance(item, dict):
            continue

        task = (item.get("task") or "").strip()

        if not task:
            continue

        saved_action_item = create_action_item(
            db=db,
            meeting_id=meeting_id,
            task=task,
            assignee=item.get("assignee"),
            due_date=item.get("due_date"),
        )

        saved_action_items.append(saved_action_item)

    return SummaryGenerateResponse(
        meeting_id=meeting_id,
        summary={
            "summary": summary.content,
            "decisions": [
                decision.content
                for decision in saved_decisions
            ],
            "action_items": [
                {
                    "task": action_item.task,
                    "assignee": action_item.assignee,
                    "due_date": action_item.due_date,
                }
                for action_item in saved_action_items
            ],
        },
    )


def get_summary_for_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> SummaryDetailResponse | None:
    """
    특정 회의의 summary 조회 서비스

    summaries, decisions, action_items를 합쳐서 반환한다.
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return None

    return _get_summary_with_children(
        db=db,
        meeting_id=meeting_id,
    )


def update_summary_for_meeting(
    db: Session,
    meeting_id: int,
    summary_data: SummaryUpdateRequest,
    current_user: User,
) -> SummaryDetailResponse | None:
    """
    특정 회의의 summary 본문만 수정

    주의
    ----
    - decisions는 decision_router.py에서 개별 추가/수정/삭제한다.
    - action_items는 action_item_router.py에서 개별 추가/수정/삭제한다.
    - 이 함수는 summaries.content, 즉 회의 요약 본문만 수정한다.
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        return None

    summary = get_summary_by_meeting_id(
        db=db,
        meeting_id=meeting_id,
    )

    if summary is None:
        return None

    update_summary(
        db=db,
        summary=summary,
        content=summary_data.summary,
    )

    return _get_summary_with_children(
        db=db,
        meeting_id=meeting_id,
    )