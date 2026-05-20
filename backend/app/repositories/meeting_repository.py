"""
meeting_repository.py

Meeting 테이블에 대한 DB 접근 로직을 담당하는 Repository

역할
- 회의 생성
- 로그인 사용자 기준 회의 단건 조회
- 로그인 사용자 기준 회의 목록 조회
- 폴더 기준 회의 목록 조회
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

    folder_id가 있으면 해당 폴더에 속한 회의로 생성한다.
    folder_id가 None이면 폴더 미지정 회의로 생성한다.
    """

    meeting = Meeting(
        user_id=user_id,
        folder_id=meeting_data.folder_id,
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
    특정 사용자의 전체 회의 목록 조회
    """

    return (
        db.query(Meeting)
        .filter(Meeting.user_id == user_id)
        .order_by(Meeting.created_at.desc())
        .offset(skip)
        .limit(limit)
        .all()
    )


def get_meetings_by_user_id_and_folder_id(
    db: Session,
    user_id: int,
    folder_id: int | None,
    skip: int = 0,
    limit: int = 100,
) -> list[Meeting]:
    """
    특정 사용자의 특정 폴더 회의 목록 조회

    folder_id가 None이면 폴더 미지정 회의를 조회한다.
    """

    query = (
        db.query(Meeting)
        .filter(Meeting.user_id == user_id)
    )

    if folder_id is None:
        query = query.filter(Meeting.folder_id.is_(None))
    else:
        query = query.filter(Meeting.folder_id == folder_id)

    return (
        query
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

    주의
    ----
    folder_id는 None으로 수정할 수도 있어야 하므로
    단순히 if meeting_data.folder_id is not None 방식으로 처리하지 않는다.
    """

    if meeting_data.title is not None:
        meeting.title = meeting_data.title

    # folder_id는 null로 변경하는 요청도 반영해야 하므로
    # 요청에 folder_id 필드가 포함되었는지 확인한다.
    if "folder_id" in meeting_data.model_fields_set:
        meeting.folder_id = meeting_data.folder_id

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