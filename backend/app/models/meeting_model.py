"""
meeting_model.py

회의(Meeting) 테이블 ORM 모델

역할
- 회의의 기본 정보를 저장
- 어떤 사용자가 만든 회의인지 user_id로 저장
- transcript, summary, image의 상위 엔티티 역할

예시 저장 데이터
- 사용자 ID
- 회의 제목
- 회의 설명
- 회의 생성 시각
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
    #
    # users 테이블의 id를 참조한다.
    # 로그인 기능이 추가되면 회의는 반드시 특정 사용자에게 속해야 한다.
    user_id = Column(
        Integer,
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # 회의 제목
    title = Column(String(255), nullable=False)

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

    # 하나의 사용자는 여러 회의를 가질 수 있음
    #
    # user_model.py의 User 모델에도 아래 관계가 있어야 한다.
    # meetings = relationship("Meeting", back_populates="user")
    user = relationship(
        "User",
        back_populates="meetings",
    )

    # 하나의 회의는 여러 transcript를 가질 수 있음
    #
    # 오디오 파일을 여러 번 업로드할 수 있으므로
    # transcript는 여러 개를 허용한다.
    transcripts = relationship(
        "Transcript",
        back_populates="meeting",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )

    # 하나의 회의는 하나의 summary만 가질 수 있음
    #
    # summary를 여러 개 저장하지 않고,
    # 같은 meeting_id에 대해 기존 summary를 갱신하는 구조로 사용한다.
    #
    # Summary 모델에도 아래 관계가 있어야 한다.
    # meeting = relationship("Meeting", back_populates="summary")
    summary = relationship(
        "Summary",
        back_populates="meeting",
        cascade="all, delete-orphan",
        passive_deletes=True,
        uselist=False,
    )

    # 하나의 회의는 여러 이미지를 가질 수 있음
    #
    # 회의 중 사진, 화이트보드, 문서 이미지를 여러 장 업로드할 수 있으므로
    # image는 여러 개를 허용한다.
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