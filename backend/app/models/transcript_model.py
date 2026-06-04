"""
transcript_model.py

회의 음성의 STT 결과를 저장하는 Transcript ORM 모델

역할
- 업로드된 오디오 파일의 STT 결과 텍스트 저장
- 어떤 회의에 속한 transcript인지 연결

예시 저장 데이터
- 회의 발화 내용
- STT 변환 결과
- transcript 생성 시각
"""

from sqlalchemy import Column, DateTime, ForeignKey, Integer, Text
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from models.base import Base


class Transcript(Base):
    """
    STT 결과 텍스트를 저장하는 ORM 모델
    """

    __tablename__ = "transcripts"

    # -------------------------
    # 기본 컬럼
    # -------------------------

    # transcript 고유 ID
    id = Column(Integer, primary_key=True, index=True)

    # 어떤 회의에 속하는 transcript인지 연결
    meeting_id = Column(
        Integer,
        ForeignKey("meetings.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # STT 결과 텍스트
    content = Column(Text, nullable=False)

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

    meeting = relationship("Meeting", back_populates="transcripts")

    def __repr__(self) -> str:
        return f"<Transcript id={self.id} meeting_id={self.meeting_id}>"