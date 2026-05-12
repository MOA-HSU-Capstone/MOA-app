"""
user_model.py

사용자(User) 테이블 모델

역할
- 회원가입한 사용자 정보를 DB에 저장
- username을 로그인 ID로 사용
- 비밀번호는 원문이 아니라 해시된 값으로 저장
- 사용자가 생성한 회의 목록과 연결
"""

from sqlalchemy import Column, DateTime, Integer, String
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from models.base import Base


class User(Base):
    """
    사용자 테이블

    id              : 사용자 고유 ID
    username        : 로그인에 사용할 사용자 ID
    hashed_password : 해시 처리된 비밀번호
    created_at      : 가입 날짜
    meetings        : 사용자가 생성한 회의 목록
    """

    __tablename__ = "users"

    # 사용자 고유 ID
    id = Column(Integer, primary_key=True, index=True)

    # 로그인에 사용할 사용자 ID
    #
    # username으로 로그인할 것이므로 unique=True가 필요하다.
    # 같은 username을 가진 사용자가 2명 있으면 로그인할 때 구분할 수 없다.
    username = Column(
        String(100),
        unique=True,
        index=True,
        nullable=False,
    )

    # 해시 처리된 비밀번호
    hashed_password = Column(String(255), nullable=False)

    # 가입 날짜
    created_at = Column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False,
    )

    # -------------------------
    # 관계 설정
    # -------------------------

    # 하나의 사용자는 여러 회의를 가질 수 있음
    #
    # Meeting 모델의 user 관계와 연결된다.
    # Meeting.user_id가 users.id를 참조한다.
    meetings = relationship(
        "Meeting",
        back_populates="user",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )

    def __repr__(self) -> str:
        return f"<User id={self.id} username={self.username!r}>"