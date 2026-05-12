"""
meeting_model.py

회의(Meeting) 테이블 ORM 모델

역할
- 회의의 기본 정보를 저장
- 어떤 사용자가 만든 회의인지 user_id로 저장
- 회의 날짜 / 시간 / 참석자 정보 저장
- transcript, summary, image의 상위 엔티티 역할
"""

from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from models.base import Base


class Meeting(Base):
    """
    회의 기본 정보를 저장하는 ORM 모델
    """

    __tablename__ = "meetings"

    # -------------------------
    # 기본 컬럼
    # -------------------------

    # 회의 고유 ID
    id = Column(Integer, primary_key=True, index=True)

    # 이 회의를 소유한 사용자 ID
    user_id = Column(
        Integer,
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # 회의 제목
    title = Column(String(255), nullable=False)

    # 회의 날짜
    # 예: "2026.05.12"
    meeting_date = Column(String(20), nullable=True)

    # 회의 시간
    # 예: "오후 2:00"
    meeting_time = Column(String(20), nullable=True)

    # 참석자 목록
    # API에서는 ["홍길동", "김철수"] 형태로 주고받지만,
    # DB에는 "홍길동,김철수" 문자열로 저장한다.
    attendees = Column(Text, nullable=True)

    # 회의 설명
    description = Column(Text, nullable=True)

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

    user = relationship(
        "User",
        back_populates="meetings",
    )

    transcripts = relationship(
        "Transcript",
        back_populates="meeting",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )

    summary = relationship(
        "Summary",
        back_populates="meeting",
        cascade="all, delete-orphan",
        passive_deletes=True,
        uselist=False,
    )

    images = relationship(
        "Image",
        back_populates="meeting",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )

    def __repr__(self) -> str:
        return (
            f"<Meeting id={self.id} "
            f"user_id={self.user_id} "
            f"title={self.title!r}>"
        )