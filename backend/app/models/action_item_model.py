"""
action_item_model.py

회의 할 일(ActionItem) 모델

역할
- 회의별 할 일을 별도 테이블로 저장
- task, assignee, due_date를 개별 수정 가능하게 한다.
"""

from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, func

from models.base import Base


class ActionItem(Base):
    __tablename__ = "action_items"

    id = Column(Integer, primary_key=True, index=True)

    meeting_id = Column(
        Integer,
        ForeignKey("meetings.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    task = Column(String(255), nullable=False)
    assignee = Column(String(100), nullable=True)
    due_date = Column(String(20), nullable=True)

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