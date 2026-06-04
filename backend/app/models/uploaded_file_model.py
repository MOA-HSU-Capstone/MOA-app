"""
uploaded_file_model.py

업로드된 파일 메타데이터 ORM 모델

역할
- 실제 파일 자체는 서버 폴더에 저장한다.
- DB에는 파일명, 저장 경로, 파일 종류, 용량 같은 메타데이터만 저장한다.
- 회의 상세 화면의 파일 탭에서 오디오/이미지/PDF 파일 목록을 조회할 수 있게 한다.

저장 대상 예시
-------------
uploads/users/{user_id}/meetings/{meeting_id}/audio/...
uploads/users/{user_id}/meetings/{meeting_id}/images/...
"""

from __future__ import annotations

from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from models.base import Base


class UploadedFile(Base):
    """
    업로드된 파일 정보를 저장하는 테이블

    주의
    ----
    - 파일 바이너리 자체를 DB에 저장하지 않는다.
    - saved_path에는 서버에 저장된 실제 파일 경로만 저장한다.
    - original_name에는 사용자가 업로드한 원본 파일명을 저장한다.
    """

    __tablename__ = "uploaded_files"

    # 파일 메타데이터 고유 ID
    id = Column(Integer, primary_key=True, index=True)

    # 어떤 회의에 업로드된 파일인지 저장
    meeting_id = Column(
        Integer,
        ForeignKey("meetings.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # 사용자가 업로드한 원본 파일명
    # 예: "4분 26초.mp3", "moa_meeting_ocr_fixed.pdf"
    original_name = Column(String(255), nullable=False)

    # 서버에 실제 저장된 파일 경로
    # 예: uploads/users/4/meetings/41/audio/uuid.mp3
    saved_path = Column(Text, nullable=False)

    # 파일 종류
    # audio / image / pdf
    file_type = Column(String(20), nullable=False, index=True)

    # MIME 타입
    # 예: audio/mpeg, application/pdf, image/png
    mime_type = Column(String(100), nullable=True)

    # 파일 크기(byte)
    size_bytes = Column(Integer, nullable=True)

    # 업로드 메타데이터 생성 시간
    created_at = Column(DateTime, server_default=func.now(), nullable=False)

    # Meeting과의 관계
    meeting = relationship(
        "Meeting",
        back_populates="uploaded_files",
    )

    def __repr__(self) -> str:
        return (
            f"<UploadedFile id={self.id} "
            f"meeting_id={self.meeting_id} "
            f"file_type={self.file_type!r} "
            f"original_name={self.original_name!r}>"
        )