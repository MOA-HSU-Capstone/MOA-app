"""
image_model.py

업로드된 이미지 및 OCR/분석 결과를 저장하는 Image ORM 모델

역할
- 이미지 파일 경로 저장
- 이미지 종류 저장
- OCR 결과 저장
- 화이트보드 이미지는 image_type으로 구분하여 함께 관리

image_type 예시
- "image"      : 일반 이미지
- "whiteboard" : 화이트보드 이미지

analysis_text 예시
- 일반 이미지 설명
- 화이트보드 구조 분석 결과
"""

from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from models.base import Base


class Image(Base):
    """
    이미지 업로드 정보 및 OCR/분석 결과를 저장하는 ORM 모델
    """

    __tablename__ = "images"

    # -------------------------
    # 기본 컬럼
    # -------------------------

    # image 고유 ID
    id = Column(Integer, primary_key=True, index=True)

    # 어떤 회의에 속하는 이미지인지 연결
    meeting_id = Column(
        Integer,
        ForeignKey("meetings.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # 업로드된 파일 경로
    file_path = Column(String(500), nullable=False)

    # 이미지 종류
    image_type = Column(String(50), nullable=False, default="image")

    # OCR 추출 결과 텍스트
    ocr_text = Column(Text, nullable=True)

    # 이미지 분석 결과 텍스트
    # 화이트보드 이미지인 경우 구조 설명/분석 결과를 담을 수 있음
    analysis_text = Column(Text, nullable=True)

    # 생성 시간
    created_at = Column(DateTime, server_default=func.now(), nullable=False)

    # 수정 시간
    updated_at = Column(
        DateTime,
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )

    # -------------------------
    # 관계 설정
    # -------------------------

    meeting = relationship("Meeting", back_populates="images")

    def __repr__(self) -> str:
        return (
            f"<Image id={self.id} meeting_id={self.meeting_id} "
            f"image_type={self.image_type!r}>"
        )