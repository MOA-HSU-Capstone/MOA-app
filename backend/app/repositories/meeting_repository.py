"""
meeting_repository.py

Meeting 테이블에 대한 DB 접근 로직을 담당하는 Repository

역할
- 회의 생성
- 로그인 사용자 기준 회의 단건 조회
- 로그인 사용자 기준 회의 목록 조회
- 회의 수정
- 회의 삭제

주의
- 비즈니스 로직은 services 계층에서 처리
- 이 파일은 DB CRUD에 집중
- 로그인 기능이 있으므로 meeting_id만으로 조회하지 않고 user_id를 함께 사용한다.
"""

from __future__ import annotations

from typing import Optional

from sqlalchemy.orm import Session

from models.meeting_model import Meeting
from schemas.meeting_schema import MeetingCreate, MeetingUpdate


def create_meeting(
    db: Session,
    meeting_data: MeetingCreate,
    user_id: int,
    attendees_text: str | None = None,
) -> Meeting:
    """
    회의 생성
    """

    meeting = Meeting(
        user_id=user_id,
        title=meeting_data.title,
        meeting_date=meeting_data.meeting_date,
        meeting_time=meeting_data.meeting_time,
        attendees=attendees_text,
        description=meeting_data.description,
    )

    db.add(meeting)
    db.commit()
    db.refresh(meeting)

    return meeting


def get_meeting_by_id(db: Session, meeting_id: int) -> Optional[Meeting]:
    """
    회의 ID로 단건 조회

    주의
    ----
    이 함수는 user_id 검사를 하지 않는다.
    """

    return (
        db.query(Meeting)
        .filter(Meeting.id == meeting_id)
        .first()
    )


def get_meeting_by_id_and_user_id(
    db: Session,
    meeting_id: int,
    user_id: int,
) -> Optional[Meeting]:
    """
    meeting_id와 user_id로 회의 단건 조회
    """

    return (
        db.query(Meeting)
        .filter(
            Meeting.id == meeting_id,
            Meeting.user_id == user_id,
        )
        .first()
    )


def get_meetings_by_user_id(
    db: Session,
    user_id: int,
    skip: int = 0,
    limit: int = 100,
) -> list[Meeting]:
    """
    특정 사용자의 회의 목록 조회
    """

    return (
        db.query(Meeting)
        .filter(Meeting.user_id == user_id)
        .order_by(Meeting.created_at.desc())
        .offset(skip)
        .limit(limit)
        .all()
    )


def get_all_meetings(db: Session, skip: int = 0, limit: int = 100) -> list[Meeting]:
    """
    전체 회의 목록 조회

    관리자 기능이나 테스트 용도로만 사용하는 것을 권장한다.
    """

    return (
        db.query(Meeting)
        .order_by(Meeting.created_at.desc())
        .offset(skip)
        .limit(limit)
        .all()
    )


def update_meeting(
    db: Session,
    meeting: Meeting,
    meeting_data: MeetingUpdate,
    attendees_text: str | None = None,
) -> Meeting:
    """
    회의 수정
    """

    if meeting_data.title is not None:
        meeting.title = meeting_data.title

    if meeting_data.meeting_date is not None:
        meeting.meeting_date = meeting_data.meeting_date

    if meeting_data.meeting_time is not None:
        meeting.meeting_time = meeting_data.meeting_time

    if meeting_data.attendees is not None:
        meeting.attendees = attendees_text

    if meeting_data.description is not None:
        meeting.description = meeting_data.description

    db.commit()
    db.refresh(meeting)

    return meeting


def delete_meeting(db: Session, meeting: Meeting) -> None:
    """
    회의 삭제
    """

    db.delete(meeting)
    db.commit()