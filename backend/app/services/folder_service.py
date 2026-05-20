"""
folder_service.py

폴더(Folder) 서비스 계층

역할
- 현재 로그인한 사용자 기준 폴더 생성
- 현재 로그인한 사용자 기준 폴더 목록 조회
- 현재 로그인한 사용자 기준 폴더 수정
- 현재 로그인한 사용자 기준 폴더 삭제
- 현재 로그인한 사용자 기준 특정 폴더의 회의 목록 조회

주의
- 다른 사용자의 폴더에 접근하지 못하도록 user_id 기준으로 검증한다.
"""

from __future__ import annotations

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from models.user_model import User
from repositories.folder_repository import (
    create_folder,
    delete_folder,
    get_folder_by_id_and_user_id,
    get_folders_by_user_id,
    update_folder,
)
from repositories.meeting_repository import get_meetings_by_user_id_and_folder_id
from schemas.folder_schema import FolderCreate, FolderUpdate
from services.meeting_service import meeting_to_response


def create_user_folder(
    db: Session,
    folder_data: FolderCreate,
    current_user: User,
):
    """
    현재 로그인한 사용자의 폴더 생성
    """

    return create_folder(
        db=db,
        user_id=current_user.id,
        name=folder_data.name,
    )


def get_user_folders(
    db: Session,
    current_user: User,
):
    """
    현재 로그인한 사용자의 폴더 목록 조회
    """

    return get_folders_by_user_id(
        db=db,
        user_id=current_user.id,
    )


def update_user_folder(
    db: Session,
    folder_id: int,
    folder_data: FolderUpdate,
    current_user: User,
):
    """
    현재 로그인한 사용자의 폴더 이름 수정
    """

    folder = get_folder_by_id_and_user_id(
        db=db,
        folder_id=folder_id,
        user_id=current_user.id,
    )

    if folder is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="폴더를 찾을 수 없습니다.",
        )

    if folder_data.name is None:
        return folder

    return update_folder(
        db=db,
        folder=folder,
        name=folder_data.name,
    )


def delete_user_folder(
    db: Session,
    folder_id: int,
    current_user: User,
):
    """
    현재 로그인한 사용자의 폴더 삭제

    주의
    ----
    폴더 안의 회의 자체를 삭제하는 것이 아니라,
    회의의 folder_id만 NULL로 두는 구조를 추천한다.
    """

    folder = get_folder_by_id_and_user_id(
        db=db,
        folder_id=folder_id,
        user_id=current_user.id,
    )

    if folder is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="폴더를 찾을 수 없습니다.",
        )

    delete_folder(
        db=db,
        folder=folder,
    )

    return {
        "message": "폴더가 삭제되었습니다.",
        "folder_id": folder_id,
    }


def get_user_folder_meetings(
    db: Session,
    folder_id: int,
    current_user: User,
    skip: int = 0,
    limit: int = 100,
):
    """
    현재 로그인한 사용자의 특정 폴더에 속한 회의 목록 조회

    GET /folders/{folder_id}/meetings
    """

    folder = get_folder_by_id_and_user_id(
        db=db,
        folder_id=folder_id,
        user_id=current_user.id,
    )

    if folder is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="폴더를 찾을 수 없습니다.",
        )

    meetings = get_meetings_by_user_id_and_folder_id(
        db=db,
        user_id=current_user.id,
        folder_id=folder_id,
        skip=skip,
        limit=limit,
    )

    return [
        meeting_to_response(meeting)
        for meeting in meetings
    ]