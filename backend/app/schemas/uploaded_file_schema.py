"""
uploaded_file_schema.py

업로드 파일 메타데이터 스키마

역할
- uploaded_files 테이블에 저장할 데이터 구조 정의
- 회의 상세 화면의 파일 탭 조회 응답 구조 정의
"""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict


class UploadedFileCreate(BaseModel):
    """
    업로드 파일 메타데이터 생성 요청 내부 스키마

    이 스키마는 클라이언트가 직접 보내는 값이 아니라,
    audio_service / image_service에서 파일 저장 후 내부적으로 사용한다.
    """

    meeting_id: int
    original_name: str
    saved_path: str
    file_type: str
    mime_type: str | None = None
    size_bytes: int | None = None


class UploadedFileResponse(BaseModel):
    """
    업로드 파일 메타데이터 응답 스키마
    """

    id: int
    meeting_id: int
    original_name: str
    saved_path: str
    file_type: str
    mime_type: str | None = None
    size_bytes: int | None = None
    created_at: datetime | None = None

    model_config = ConfigDict(from_attributes=True)


class MeetingFilesResponse(BaseModel):
    """
    회의 상세 화면 파일 탭 응답 스키마

    audio_files
    - mp3, wav, m4a 등 오디오 파일 목록

    image_files
    - 이미지 파일과 PDF 파일 목록
    """

    meeting_id: int
    audio_files: list[UploadedFileResponse]
    image_files: list[UploadedFileResponse]