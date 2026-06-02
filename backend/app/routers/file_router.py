"""
file_router.py

회의 업로드 파일 조회 API 라우터

역할
- 회의 상세 화면의 파일 탭에서 사용할 파일 목록 조회
- 오디오 파일 목록과 이미지/PDF 파일 목록을 반환
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from config.database import get_db
from utils.auth_dependency import get_current_user
from models.user_model import User
from schemas.uploaded_file_schema import MeetingFilesResponse
from services.file_service import get_meeting_uploaded_files


router = APIRouter(
    prefix="/meetings",
    tags=["files"],
)


@router.get(
    "/{meeting_id}/files",
    response_model=MeetingFilesResponse,
)
def get_meeting_files(
    meeting_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> MeetingFilesResponse:
    """
    회의 상세 화면 파일 탭 조회 API

    요청 예시
    --------
    GET /meetings/41/files
    Authorization: Bearer {access_token}

    응답 예시
    --------
    {
        "meeting_id": 41,
        "audio_files": [...],
        "image_files": [...]
    }
    """

    return get_meeting_uploaded_files(
        db=db,
        meeting_id=meeting_id,
        current_user=current_user,
    )