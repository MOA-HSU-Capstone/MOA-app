"""
summary_repository.py

Summary 테이블에 대한 DB 접근 로직을 담당하는 Repository

역할
- 회의 ID로 summary 조회
- summary 생성
- summary 수정
- summary 생성 또는 갱신
- summary 삭제

가정
- 현재 프로젝트에서는 회의당 summary를 1개로 관리한다.
- 같은 meeting_id에 대해 summary가 이미 있으면 새로 만들지 않고 content를 갱신한다.
- summaries.content에는 회의 요약 본문 문자열만 저장한다.
- 결정사항은 decisions 테이블에서 관리한다.
- 할 일은 action_items 테이블에서 관리한다.
"""

from __future__ import annotations

from typing import Optional

from sqlalchemy.orm import Session

from models.summary_model import Summary
from schemas.summary_schema import SummaryCreate


def get_summary_by_meeting_id(db: Session, meeting_id: int) -> Optional[Summary]:
    """
    특정 회의의 summary 조회
    """

    return (
        db.query(Summary)
        .filter(Summary.meeting_id == meeting_id)
        .first()
    )


def create_summary(db: Session, summary_data: SummaryCreate) -> Summary:
    """
    summary 생성

    주의
    ----
    이 함수는 단순 생성만 담당한다.
    같은 meeting_id의 summary가 이미 있는지는 검사하지 않는다.
    """

    summary = Summary(
        meeting_id=summary_data.meeting_id,
        content=summary_data.content,
    )

    db.add(summary)
    db.commit()
    db.refresh(summary)

    return summary


def update_summary(
    db: Session,
    summary: Summary,
    content: str,
) -> Summary:
    """
    기존 summary 내용 수정

    content에는 회의 요약 본문 문자열을 저장한다.
    """

    summary.content = content

    db.commit()
    db.refresh(summary)

    return summary


def upsert_summary(
    db: Session,
    summary_data: SummaryCreate,
) -> Summary:
    """
    summary 생성 또는 갱신

    처리 방식
    -------
    1. meeting_id로 기존 summary 조회
    2. 있으면 content 갱신
    3. 없으면 새 summary 생성

    사용 이유
    -------
    회의당 summary를 1개만 관리하기 위해 사용한다.
    """

    existing_summary = get_summary_by_meeting_id(
        db=db,
        meeting_id=summary_data.meeting_id,
    )

    if existing_summary is not None:
        return update_summary(
            db=db,
            summary=existing_summary,
            content=summary_data.content,
        )

    return create_summary(
        db=db,
        summary_data=summary_data,
    )


def delete_summary(db: Session, summary: Summary) -> None:
    """
    summary 삭제
    """

    db.delete(summary)
    db.commit()