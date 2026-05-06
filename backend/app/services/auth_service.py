"""
auth_service.py

인증 서비스 계층

역할
- 회원가입 비즈니스 로직
- 로그인 비즈니스 로직
- 비밀번호 해시 처리
- JWT 토큰 발급
"""

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from repositories.user_repository import get_user_by_email, create_user
from schemas.user_schema import UserCreate, UserLogin
from utils.security import hash_password, verify_password, create_access_token


def register_user(db: Session, user_create: UserCreate):
    """
    회원가입 처리

    1. 이메일 중복 확인
    2. 비밀번호 해시 처리
    3. 사용자 DB 저장
    """

    existing_user = get_user_by_email(db, user_create.email)

    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="이미 가입된 이메일입니다.",
        )

    hashed_pw = hash_password(user_create.password)

    user = create_user(
        db=db,
        email=user_create.email,
        username=user_create.username,
        hashed_password=hashed_pw,
    )

    return user


def login_user(db: Session, user_login: UserLogin):
    """
    로그인 처리

    1. 이메일로 사용자 조회
    2. 비밀번호 검증
    3. JWT access token 발급
    """

    user = get_user_by_email(db, user_login.email)

    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="이메일 또는 비밀번호가 올바르지 않습니다.",
        )

    if not verify_password(user_login.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="이메일 또는 비밀번호가 올바르지 않습니다.",
        )

    access_token = create_access_token(
        data={
            "sub": str(user.id),
            "email": user.email,
        }
    )

    return access_token