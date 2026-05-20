"""
folder_router.py

폴더(Folder) API 라우터

제공 기능
- 폴더 생성
- 내 폴더 목록 조회
- 폴더 이름 수정
- 폴더 삭제
- 특정 폴더의 회의 목록 조회

인증
- 모든 API는 Authorization: Bearer {access_token} 필요
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query, status
from sqlalchemy.orm import Session

from config.database import get_db
from models.user_model import User
from schemas.folder_schema import (
    FolderCreate,
    FolderResponse,
    FolderUpdate,
)
from schemas.meeting_schema import MeetingResponse
from services.folder_service import (
    create_user_folder,
    delete_user_folder,
    get_user_folder_meetings,
    get_user_folders,
    update_user_folder,
)
from utils.auth_dependency import get_current_user


router = APIRouter(
    prefix="/folders",
    tags=["Folders"],
)


@router.post(
    "",
    response_model=FolderResponse,
    status_code=status.HTTP_201_CREATED,
    summary="폴더 생성",
)
def create_folder_api(
    folder_data: FolderCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> FolderResponse:
    """
    현재 로그인한 사용자의 폴더를 생성합니다.

    요청 예시
    --------
    {
        "name": "소프트웨어공학 회의"
    }
    """

    return create_user_folder(
        db=db,
        folder_data=folder_data,
        current_user=current_user,
    )


@router.get(
    "",
    response_model=list[FolderResponse],
    summary="내 폴더 목록 조회",
)
def read_folder_list(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> list[FolderResponse]:
    """
    현재 로그인한 사용자의 폴더 목록을 조회합니다.
    """

    return get_user_folders(
        db=db,
        current_user=current_user,
    )


@router.patch(
    "/{folder_id}",
    response_model=FolderResponse,
    summary="폴더 이름 수정",
)
def patch_folder(
    folder_id: int,
    folder_data: FolderUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> FolderResponse:
    """
    현재 로그인한 사용자의 폴더 이름을 수정합니다.

    요청 예시
    --------
    {
        "name": "수정된 폴더명"
    }
    """

    return update_user_folder(
        db=db,
        folder_id=folder_id,
        folder_data=folder_data,
        current_user=current_user,
    )


@router.delete(
    "/{folder_id}",
    status_code=status.HTTP_200_OK,
    summary="폴더 삭제",
)
def delete_folder_api(
    folder_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> dict:
    """
    현재 로그인한 사용자의 폴더를 삭제합니다.

    주의
    ----
    폴더 삭제는 회의 삭제가 아닙니다.
    회의는 유지하고 folder_id만 NULL로 처리하는 구조를 추천합니다.
    """

    return delete_user_folder(
        db=db,
        folder_id=folder_id,
        current_user=current_user,
    )


@router.get(
    "/{folder_id}/meetings",
    response_model=list[MeetingResponse],
    summary="특정 폴더의 회의 목록 조회",
)
def read_folder_meetings(
    folder_id: int,
    skip: int = Query(0, ge=0, description="건너뛸 개수"),
    limit: int = Query(100, ge=1, le=1000, description="최대 조회 개수"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> list[MeetingResponse]:
    """
    현재 로그인한 사용자의 특정 폴더에 속한 회의 목록을 조회합니다.
    """

    return get_user_folder_meetings(
        db=db,
        folder_id=folder_id,
        current_user=current_user,
        skip=skip,
        limit=limit,
    )