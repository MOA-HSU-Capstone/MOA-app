"""
meeting_router.py

회의 관련 API 라우터

역할
- 회의 생성
- 회의 목록 조회
- 회의 단건 조회
- 회의 수정
- 회의 삭제
- 회의 summary 생성
- 회의 summary 조회
- 회의 전체 transcript 조회

주의
- 실제 비즈니스 로직은 services 계층에서 처리
- 이 파일은 HTTP 요청/응답 처리에 집중
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from config.database import get_db
from schemas.meeting_schema import (
    MeetingCreate,
    MeetingResponse,
    MeetingUpdate,
)
from schemas.summary_schema import (
    SummaryDetailResponse,
    SummaryGenerateResponse,
)
from services.meeting_service import (
    create_new_meeting,
    create_summary_for_meeting,
    get_full_transcript_for_meeting,
    get_meeting_detail,
    get_meeting_list,
    get_summary_for_meeting,
    remove_meeting,
    update_meeting_detail,
)

router = APIRouter(
    prefix="/meetings",
    tags=["Meetings"],
)


@router.post(
    "",
    response_model=MeetingResponse,
    status_code=status.HTTP_201_CREATED,
    summary="회의 생성",
)
def create_meeting(
    meeting_data: MeetingCreate,
    db: Session = Depends(get_db),
) -> MeetingResponse:
    """
    새로운 회의를 생성합니다.
    """

    return create_new_meeting(db, meeting_data)


@router.get(
    "",
    response_model=list[MeetingResponse],
    summary="회의 목록 조회",
)
def read_meeting_list(
    skip: int = Query(0, ge=0, description="건너뛸 개수"),
    limit: int = Query(100, ge=1, le=1000, description="최대 조회 개수"),
    db: Session = Depends(get_db),
) -> list[MeetingResponse]:
    """
    회의 목록을 조회합니다.
    """

    return get_meeting_list(db, skip=skip, limit=limit)


@router.get(
    "/{meeting_id}",
    response_model=MeetingResponse,
    summary="회의 상세 조회",
)
def read_meeting_detail(
    meeting_id: int,
    db: Session = Depends(get_db),
) -> MeetingResponse:
    """
    특정 회의의 상세 정보를 조회합니다.
    """

    meeting = get_meeting_detail(db, meeting_id)

    if meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 회의를 찾을 수 없습니다.",
        )

    return meeting


@router.patch(
    "/{meeting_id}",
    response_model=MeetingResponse,
    summary="회의 수정",
)
def patch_meeting(
    meeting_id: int,
    meeting_data: MeetingUpdate,
    db: Session = Depends(get_db),
) -> MeetingResponse:
    """
    특정 회의의 제목/설명을 수정합니다.
    """

    updated_meeting = update_meeting_detail(db, meeting_id, meeting_data)

    if updated_meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="수정할 회의를 찾을 수 없습니다.",
        )

    return updated_meeting


@router.delete(
    "/{meeting_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    summary="회의 삭제",
)
def delete_meeting(
    meeting_id: int,
    db: Session = Depends(get_db),
) -> None:
    """
    특정 회의를 삭제합니다.
    """

    deleted = remove_meeting(db, meeting_id)

    if not deleted:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="삭제할 회의를 찾을 수 없습니다.",
        )


@router.post(
    "/{meeting_id}/summary",
    response_model=SummaryGenerateResponse,
    summary="회의 summary 생성",
)
def generate_summary(
    meeting_id: int,
    db: Session = Depends(get_db),
) -> SummaryGenerateResponse:
    """
    특정 회의의 전체 transcript를 기반으로 summary를 생성합니다.

    동작
    ----
    - transcript 전체를 합쳐서 요약
    - 기존 summary가 있으면 교체
    """

    result = create_summary_for_meeting(db, meeting_id)

    if result is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="회의가 없거나 transcript가 존재하지 않습니다.",
        )

    return result


@router.get(
    "/{meeting_id}/summary",
    response_model=SummaryDetailResponse,
    summary="회의 summary 조회",
)
def read_summary(
    meeting_id: int,
    db: Session = Depends(get_db),
) -> SummaryDetailResponse:
    """
    특정 회의의 summary를 조회합니다.
    """

    summary = get_summary_for_meeting(db, meeting_id)

    if summary is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 회의의 summary를 찾을 수 없습니다.",
        )

    return summary


@router.get(
    "/{meeting_id}/transcript",
    summary="회의 전체 transcript 조회",
)
def read_full_transcript(
    meeting_id: int,
    db: Session = Depends(get_db),
) -> dict:
    """
    특정 회의의 전체 transcript(전문)를 조회합니다.

    Returns
    -------
    dict
        {
            "meeting_id": int,
            "transcript": str
        }
    """

    transcript_text = get_full_transcript_for_meeting(db, meeting_id)

    if transcript_text is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="회의가 없거나 summary 생성에 사용할 STT/OCR 데이터가 존재하지 않습니다.",
        )

    return {
        "meeting_id": meeting_id,
        "transcript": transcript_text,
    }