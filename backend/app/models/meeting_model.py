"""
meeting_model.py

회의(Meeting) 테이블 ORM 모델

역할
- 회의의 기본 정보를 저장
- 어떤 사용자가 만든 회의인지 user_id로 저장
- 어떤 폴더에 속한 회의인지 folder_id로 저장
- 회의 날짜 / 시간 / 참석자 정보 저장
- transcript, summary, image, uploaded_files의 상위 엔티티 역할

폴더 구조
---------
User
└── Folders
    └── Meetings

주의
- folder_id는 nullable=True
- folder_id가 NULL이면 폴더 미지정 회의로 처리한다.
"""

from __future__ import annotations

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

    # 이 회의가 속한 폴더 ID
    # NULL이면 폴더 미지정 회의
    folder_id = Column(
        Integer,
        ForeignKey("folders.id", ondelete="SET NULL"),
        nullable=True,
        index=True,
    )

    # 회의 제목
    title = Column(String(255), nullable=False)

    # 회의 날짜
    # 예: "2026-06-02"
    meeting_date = Column(String(20), nullable=True)

    # 회의 시간
    # 예: "14:09"
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

    folder = relationship(
        "Folder",
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

    # 업로드된 원본 파일 메타데이터 목록
    # 회의 삭제 시 uploaded_files row도 함께 삭제된다.
    # 실제 서버 파일 삭제는 remove_meeting()에서 폴더 삭제로 처리된다.
    uploaded_files = relationship(
        "UploadedFile",
        back_populates="meeting",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )

    def __repr__(self) -> str:
        return (
            f"<Meeting id={self.id} "
            f"user_id={self.user_id} "
            f"folder_id={self.folder_id} "
            f"title={self.title!r}>"
        )