"""
file_service.py

회의 업로드 파일 목록 조회 / 보기 / 다운로드 서비스

역할
- 현재 로그인한 사용자의 회의인지 확인
- 해당 회의에 연결된 uploaded_files 목록 조회
- 오디오 파일과 이미지/PDF 파일을 나누어 반환
- file_id 기준 실제 서버 파일을 FileResponse로 반환

중요
----
- 백엔드는 파일을 서버에서 앱으로 내려주는 역할만 한다.
- Android 기기 내부 저장소의 실제 저장 위치는 앱에서 결정한다.

Android 저장 위치
---------------
PDF 문서
→ 내장 저장공간/Documents/MOA

녹음파일
→ 내장 저장공간/Recordings/MOA

사진
→ 내장 저장공간/Pictures/MOA

앱 동작 방식
-----------
1. GET /meetings/{meeting_id}/files 로 파일 목록 조회
2. 앱에서 각 파일이 위 경로에 이미 저장되어 있는지 확인
3. 없으면 버튼을 "다운로드"로 표시
4. 있으면 버튼을 "재생" 또는 "열기"로 표시
5. 다운로드 버튼 클릭 시 GET /meetings/{meeting_id}/files/{file_id}/download 호출
6. 받은 파일을 file_type에 맞는 MOA 폴더에 저장
"""

from __future__ import annotations

from pathlib import Path
from urllib.parse import quote

from fastapi import HTTPException, status
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from models.user_model import User
from repositories.meeting_repository import get_meeting_by_id_and_user_id
from repositories.uploaded_file_repository import (
    get_uploaded_file_by_id_and_meeting_id,
    get_uploaded_files_by_meeting_id,
)
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

    반환되는 파일 정보
    ----------------
    - id: 파일 ID
    - original_name: 앱 화면에 보여줄 원본 파일명
    - saved_path: 서버 내부 저장 경로
    - file_type: audio / image / pdf
    - mime_type: 파일 MIME 타입
    - size_bytes: 파일 크기

    주의
    ----
    saved_path는 서버 내부 경로이므로 앱 화면에는 original_name을 표시한다.
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


def _build_content_disposition(
    original_name: str,
    disposition_type: str,
) -> str:
    """
    Content-Disposition 헤더 생성

    disposition_type
    ----------------
    inline
    - 앱에서 바로 열기/재생할 때 사용
    - 예: 오디오 재생, 이미지 보기, PDF 미리보기

    attachment
    - 다운로드 파일로 처리할 때 사용
    - Android 앱에서 파일을 받아서 Documents/MOA, Recordings/MOA, Pictures/MOA에 저장

    한글 파일명 처리
    --------------
    filename*=UTF-8'' 형식으로 인코딩해서 한글 파일명이 깨지는 것을 줄인다.
    """

    encoded_filename = quote(original_name)

    return (
        f"{disposition_type}; "
        f"filename*=UTF-8''{encoded_filename}"
    )


def get_meeting_uploaded_file_response(
    db: Session,
    meeting_id: int,
    file_id: int,
    current_user: User,
    disposition_type: str = "inline",
) -> FileResponse:
    """
    업로드된 실제 파일을 앱으로 내려준다.

    동작 방식
    --------
    1. 현재 로그인한 사용자의 회의인지 확인
    2. file_id + meeting_id 기준 uploaded_files 조회
    3. DB에 저장된 saved_path의 실제 파일이 서버에 존재하는지 확인
    4. FileResponse로 파일 반환

    disposition_type
    ----------------
    inline
    - 파일 보기/재생용
    - GET /meetings/{meeting_id}/files/{file_id}/view

    attachment
    - 파일 다운로드용
    - GET /meetings/{meeting_id}/files/{file_id}/download

    Android 저장 위치
    ----------------
    백엔드는 저장 위치를 정하지 않는다.
    앱에서 file_type에 따라 아래 경로에 저장한다.

    PDF 문서
    → 내장 저장공간/Documents/MOA

    녹음파일
    → 내장 저장공간/Recordings/MOA

    사진
    → 내장 저장공간/Pictures/MOA
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

    uploaded_file = get_uploaded_file_by_id_and_meeting_id(
        db=db,
        file_id=file_id,
        meeting_id=meeting_id,
    )

    if uploaded_file is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="업로드 파일을 찾을 수 없습니다.",
        )

    file_path = Path(uploaded_file.saved_path)

    if not file_path.exists() or not file_path.is_file():
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="서버에 실제 파일이 존재하지 않습니다.",
        )

    headers = {
        "Content-Disposition": _build_content_disposition(
            original_name=uploaded_file.original_name,
            disposition_type=disposition_type,
        )
    }

    return FileResponse(
        path=str(file_path),
        media_type=uploaded_file.mime_type or "application/octet-stream",
        filename=uploaded_file.original_name,
        headers=headers,
    )