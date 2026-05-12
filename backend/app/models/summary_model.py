"""
summary_model.py

회의 요약 결과를 저장하는 Summary ORM 모델

역할
- transcript와 OCR 결과를 바탕으로 생성된 요약 저장
- 회의별 요약 결과 관리
- 하나의 회의는 하나의 summary만 가질 수 있음

예시 저장 데이터
- 회의 요약
- 결정 사항
- 액션 아이템 요약
"""

from sqlalchemy import Column, DateTime, ForeignKey, Integer, Text
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from models.base import Base


class Summary(Base):
    """
    회의 요약 결과를 저장하는 ORM 모델
    """

    __tablename__ = "summaries"

    # -------------------------
    # 기본 컬럼
    # -------------------------

    # summary 고유 ID
    id = Column(Integer, primary_key=True, index=True)

    # 어떤 회의의 요약인지 연결
    #
    # unique=True를 넣어서 하나의 meeting_id에는
    # summary가 하나만 생성되도록 제한한다.
    meeting_id = Column(
        Integer,
        ForeignKey("meetings.id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
        index=True,
    )

    # 요약 텍스트
    #
    # 현재는 Text로 저장한다.
    # LLM 결과가 dict라면 service 또는 repository에서
    # json.dumps(..., ensure_ascii=False)로 문자열 변환 후 저장하면 된다.
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

    # 하나의 Summary는 하나의 Meeting에 속함
    #
    # Meeting 모델의 summary 관계와 연결된다.
    # Meeting.summary = relationship(..., uselist=False)
    meeting = relationship(
        "Meeting",
        back_populates="summary",
    )

    def __repr__(self) -> str:
        return f"<Summary id={self.id} meeting_id={self.meeting_id}>"