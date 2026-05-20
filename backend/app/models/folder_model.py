"""
folder_model.py

폴더(Folder) 모델

역할
- 사용자가 회의록을 폴더별로 분류할 수 있게 한다.
- 한 사용자는 여러 개의 폴더를 가질 수 있다.
- 한 폴더에는 여러 회의가 들어갈 수 있다.

관계
- User 1 : N Folder
- Folder 1 : N Meeting
"""

from sqlalchemy import Column, Integer, String, DateTime, ForeignKey, func
from sqlalchemy.orm import relationship

from config.database import Base


class Folder(Base):
    __tablename__ = "folders"

    id = Column(Integer, primary_key=True, index=True)

    # 폴더 소유자
    user_id = Column(
        Integer,
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # 폴더 이름
    name = Column(String(100), nullable=False)

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

    # -------------------------
    # 관계 설정
    # -------------------------

    meetings = relationship(
        "Meeting",
        back_populates="folder",
    )

    def __repr__(self) -> str:
        return (
            f"<Folder id={self.id} "
            f"user_id={self.user_id} "
            f"name={self.name!r}>"
        )