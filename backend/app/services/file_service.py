"""
file_service.py

회의 업로드 파일 목록 조회 서비스

역할
- 현재 로그인한 사용자의 회의인지 확인
- 해당 회의에 연결된 uploaded_files 목록 조회
- 오디오 파일과 이미지/PDF 파일을 나누어 반환
"""

from __future__ import annotations

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from models.user_model import User
from repositories.meeting_repository import get_meeting_by_id_and_user_id
from repositories.uploaded_file_repository import get_uploaded_files_by_meeting_id
from schemas.uploaded_file_schema import (
    MeetingFilesResponse,
    UploadedFileResponse,
)


def get_meeting_uploaded_files(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> MeetingFilesResponse:
    """
    특정 회의의 업로드 파일 목록 조회

    동작 방식
    --------
    1. 현재 로그인한 사용자의 회의인지 확인
    2. meeting_id 기준 uploaded_files 조회
    3. audio_files와 image_files로 분리
    4. MeetingFilesResponse 반환
    """

    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 회의를 찾을 수 없습니다.",
        )

    uploaded_files = get_uploaded_files_by_meeting_id(
        db=db,
        meeting_id=meeting_id,
    )

    audio_files: list[UploadedFileResponse] = []
    image_files: list[UploadedFileResponse] = []

    for uploaded_file in uploaded_files:
        response = UploadedFileResponse.model_validate(uploaded_file)

        if uploaded_file.file_type == "audio":
            audio_files.append(response)

        elif uploaded_file.file_type in {"image", "pdf"}:
            image_files.append(response)

    return MeetingFilesResponse(
        meeting_id=meeting_id,
        audio_files=audio_files,
        image_files=image_files,
    )