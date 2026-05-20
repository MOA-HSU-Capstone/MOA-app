"""
folder_repository.py

폴더(Folder) DB 접근 계층

역할
- 폴더 생성
- 로그인 사용자 기준 폴더 목록 조회
- folder_id + user_id 기준 폴더 조회
- 폴더 이름 수정
- 폴더 삭제

주의
- repository는 DB CRUD만 담당한다.
- 현재 로그인한 사용자의 폴더인지 확인하는 로직은 service 계층에서 사용한다.
"""

from __future__ import annotations

from typing import Optional

from sqlalchemy.orm import Session

from models.folder_model import Folder


def create_folder(
    db: Session,
    user_id: int,
    name: str,
) -> Folder:
    """
    폴더 생성
    """

    folder = Folder(
        user_id=user_id,
        name=name,
    )

    db.add(folder)
    db.commit()
    db.refresh(folder)

    return folder


def get_folders_by_user_id(
    db: Session,
    user_id: int,
) -> list[Folder]:
    """
    특정 사용자의 폴더 목록 조회
    """

    return (
        db.query(Folder)
        .filter(Folder.user_id == user_id)
        .order_by(Folder.created_at.desc())
        .all()
    )


def get_folder_by_id_and_user_id(
    db: Session,
    folder_id: int,
    user_id: int,
) -> Optional[Folder]:
    """
    folder_id와 user_id로 폴더 단건 조회

    다른 사용자의 폴더 접근을 막기 위해 user_id를 함께 사용한다.
    """

    return (
        db.query(Folder)
        .filter(
            Folder.id == folder_id,
            Folder.user_id == user_id,
        )
        .first()
    )


def update_folder(
    db: Session,
    folder: Folder,
    name: str,
) -> Folder:
    """
    폴더 이름 수정
    """

    folder.name = name

    db.commit()
    db.refresh(folder)

    return folder


def delete_folder(
    db: Session,
    folder: Folder,
) -> None:
    """
    폴더 삭제

    주의
    ----
    폴더 삭제 시 meetings.folder_id는 NULL로 처리하는 방식을 추천한다.
    실제 처리는 meeting_model.py의 ForeignKey ondelete="SET NULL" 설정과
    DB 동작 방식에 따라 달라질 수 있다.
    """

    db.delete(folder)
    db.commit()