"""
decision_model.py

회의 결정사항(Decision) 모델

역할
- 회의별 결정사항을 별도 테이블로 저장
- 한 회의는 여러 개의 결정사항을 가질 수 있다.
"""

from sqlalchemy import Column, Integer, Text, DateTime, ForeignKey, func

from models.base import Base


class Decision(Base):
    __tablename__ = "decisions"

    id = Column(Integer, primary_key=True, index=True)

    meeting_id = Column(
        Integer,
        ForeignKey("meetings.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    content = Column(Text, nullable=False)

    created_at = Column(
        DateTime,
        server_default=func.now(),
        nullable=False,
    )

    updated_at = Column(
        DateTime,
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )