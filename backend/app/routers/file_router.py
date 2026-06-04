"""
file_router.py

회의 업로드 파일 조회 / 보기 / 다운로드 API 라우터

역할
- 회의 상세 화면의 파일 탭에서 사용할 파일 목록 조회
- 특정 파일을 앱에서 열거나 다운로드할 수 있게 실제 파일 반환

API 목록
--------
GET /meetings/{meeting_id}/files
- 파일 목록 조회
- 파일명, 타입, 용량, file_id를 반환한다.

GET /meetings/{meeting_id}/files/{file_id}/view
- 파일 보기/재생용
- 오디오 재생, 이미지 보기, PDF 미리보기 등에 사용한다.

GET /meetings/{meeting_id}/files/{file_id}/download
- 파일 다운로드용
- Android 앱이 파일을 내려받아 내부 저장소의 MOA 폴더에 저장한다.

Android 저장 위치
---------------
PDF 문서
→ 내장 저장공간/Documents/MOA

녹음파일
→ 내장 저장공간/Recordings/MOA

사진
→ 내장 저장공간/Pictures/MOA

앱 버튼 처리
-----------
- 파일이 위 경로에 없으면 버튼: 다운로드
- 파일이 위 경로에 이미 있으면 버튼: 재생 또는 열기
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from config.database import get_db
from utils.auth_dependency import get_current_user
from models.user_model import User
from schemas.uploaded_file_schema import MeetingFilesResponse
from services.file_service import (
    get_meeting_uploaded_file_response,
    get_meeting_uploaded_files,
)


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
    GET /meetings/59/files
    Authorization: Bearer {access_token}

    응답 예시
    --------
    {
        "meeting_id": 59,
        "audio_files": [
            {
                "id": 1,
                "meeting_id": 59,
                "original_name": "회의녹음.m4a",
                "saved_path": "uploads/users/4/meetings/59/audio/uuid.m4a",
                "file_type": "audio",
                "mime_type": "audio/mp4",
                "size_bytes": 1041000,
                "created_at": "2026-06-02T15:00:00"
            }
        ],
        "image_files": [
            {
                "id": 2,
                "meeting_id": 59,
                "original_name": "문서.pdf",
                "saved_path": "uploads/users/4/meetings/59/images/uuid.pdf",
                "file_type": "pdf",
                "mime_type": "application/pdf",
                "size_bytes": 58000,
                "created_at": "2026-06-02T15:01:00"
            }
        ]
    }

    앱 사용 방식
    -----------
    - 화면에는 original_name을 표시한다.
    - saved_path는 서버 내부 경로이므로 화면에 직접 표시하지 않는다.
    - file_type에 따라 앱 내부 저장 위치를 결정한다.
    """

    return get_meeting_uploaded_files(
        db=db,
        meeting_id=meeting_id,
        current_user=current_user,
    )


@router.get(
    "/{meeting_id}/files/{file_id}/view",
    response_class=FileResponse,
)
def view_meeting_file(
    meeting_id: int,
    file_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> FileResponse:
    """
    업로드 파일 보기/재생 API

    사용 목적
    --------
    - 오디오 파일 재생
    - 이미지 파일 보기
    - PDF 파일 미리보기

    요청 예시
    --------
    GET /meetings/59/files/2/view
    Authorization: Bearer {access_token}

    반환
    ----
    - 실제 파일 바이너리
    - Content-Disposition: inline
    - media_type은 uploaded_files.mime_type 기준

    앱 처리 예시
    -----------
    - file_type == "audio"이면 재생
    - file_type == "image"이면 이미지 열기
    - file_type == "pdf"이면 PDF 열기
    """

    return get_meeting_uploaded_file_response(
        db=db,
        meeting_id=meeting_id,
        file_id=file_id,
        current_user=current_user,
        disposition_type="inline",
    )


@router.get(
    "/{meeting_id}/files/{file_id}/download",
    response_class=FileResponse,
)
def download_meeting_file(
    meeting_id: int,
    file_id: int,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> FileResponse:
    """
    업로드 파일 다운로드 API

    요청 예시
    --------
    GET /meetings/59/files/2/download
    Authorization: Bearer {access_token}

    반환
    ----
    - 실제 파일 바이너리
    - Content-Disposition: attachment
    - 원본 파일명 유지

    Android 저장 위치
    ----------------
    앱은 다운로드 받은 파일을 file_type에 따라 아래 위치에 저장한다.

    PDF 문서
    → 내장 저장공간/Documents/MOA

    녹음파일
    → 내장 저장공간/Recordings/MOA

    사진
    → 내장 저장공간/Pictures/MOA

    버튼 처리
    --------
    - 저장 경로에 같은 이름의 파일이 없으면 다운로드 버튼 표시
    - 저장 경로에 같은 이름의 파일이 있으면 재생/열기 버튼 표시
    """

    return get_meeting_uploaded_file_response(
        db=db,
        meeting_id=meeting_id,
        file_id=file_id,
        current_user=current_user,
        disposition_type="attachment",
    )