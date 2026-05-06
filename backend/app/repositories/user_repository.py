"""
user_repository.py

사용자 DB 접근 계층

역할
- 사용자 생성
- 이메일로 사용자 조회
- ID로 사용자 조회
"""

from sqlalchemy.orm import Session

from models.user_model import User


def get_user_by_email(db: Session, email: str) -> User | None:
    """
    이메일로 사용자 조회

    회원가입 시 이메일 중복 확인
    로그인 시 사용자 확인에 사용
    """

    return db.query(User).filter(User.email == email).first()


def get_user_by_id(db: Session, user_id: int) -> User | None:
    """
    사용자 ID로 사용자 조회
    """

    return db.query(User).filter(User.id == user_id).first()


def create_user(
    db: Session,
    email: str,
    username: str,
    hashed_password: str,
) -> User:
    """
    사용자 생성

    비밀번호는 반드시 이미 해시된 값을 받아야 한다.
    """

    user = User(
        email=email,
        username=username,
        hashed_password=hashed_password,
    )

    db.add(user)
    db.commit()
    db.refresh(user)

    return user