"""
uploaded_file_repository.py

업로드 파일 메타데이터 DB 접근 로직

역할
- 업로드 파일 메타데이터 생성
- meeting_id 기준 파일 목록 조회
- meeting_id + file_type 기준 파일 목록 조회
"""

from __future__ import annotations

from sqlalchemy.orm import Session

from models.uploaded_file_model import UploadedFile
from schemas.uploaded_file_schema import UploadedFileCreate


def create_uploaded_file(
    db: Session,
    file_data: UploadedFileCreate,
) -> UploadedFile:
    """
    업로드 파일 메타데이터 생성

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    file_data : UploadedFileCreate
        저장할 파일 메타데이터
    """

    uploaded_file = UploadedFile(
        meeting_id=file_data.meeting_id,
        original_name=file_data.original_name,
        saved_path=file_data.saved_path,
        file_type=file_data.file_type,
        mime_type=file_data.mime_type,
        size_bytes=file_data.size_bytes,
    )

    db.add(uploaded_file)
    db.commit()
    db.refresh(uploaded_file)

    return uploaded_file


def get_uploaded_files_by_meeting_id(
    db: Session,
    meeting_id: int,
) -> list[UploadedFile]:
    """
    특정 회의에 업로드된 모든 파일 메타데이터 조회

    Returns
    -------
    list[UploadedFile]
        최신 업로드 순으로 정렬된 파일 목록
    """

    return (
        db.query(UploadedFile)
        .filter(UploadedFile.meeting_id == meeting_id)
        .order_by(UploadedFile.created_at.desc())
        .all()
    )


def get_uploaded_files_by_meeting_id_and_type(
    db: Session,
    meeting_id: int,
    file_type: str,
) -> list[UploadedFile]:
    """
    특정 회의에 업로드된 파일 중 file_type이 일치하는 것만 조회

    file_type 예시
    -------------
    - audio
    - image
    - pdf
    """

    return (
        db.query(UploadedFile)
        .filter(
            UploadedFile.meeting_id == meeting_id,
            UploadedFile.file_type == file_type,
        )
        .order_by(UploadedFile.created_at.desc())
        .all()
    )