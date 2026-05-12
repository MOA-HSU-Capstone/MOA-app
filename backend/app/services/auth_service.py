"""
auth_service.py

인증 서비스 계층

역할
- 회원가입 비즈니스 로직
- 로그인 비즈니스 로직
- 비밀번호 해시 처리
- JWT 토큰 발급

현재 인증 방식
- email 사용 X
- username + password 사용
"""

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from repositories.user_repository import get_user_by_username, create_user
from schemas.user_schema import UserCreate, UserLogin
from utils.security import hash_password, verify_password, create_access_token


def register_user(db: Session, user_create: UserCreate):
    """
    회원가입 처리

    처리 과정
    -------
    1. username 중복 확인
    2. 비밀번호 해시 처리
    3. 사용자 DB 저장

    주의
    ----
    비밀번호 원문은 절대 DB에 저장하지 않는다.
    """

    # username 중복 확인
    existing_user = get_user_by_username(db, user_create.username)

    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="이미 사용 중인 사용자 이름입니다.",
        )

    # 비밀번호 해시 처리
    hashed_password = hash_password(user_create.password)

    # 사용자 DB 저장
    # create_user는 DB 저장만 담당한다.
    user = create_user(
        db=db,
        username=user_create.username,
        hashed_password=hashed_password,
    )

    return user


def login_user(db: Session, user_login: UserLogin):
    """
    로그인 처리

    처리 과정
    -------
    1. username으로 사용자 조회
    2. 비밀번호 검증
    3. JWT access token 발급

    주의
    ----
    보안을 위해 username이 틀렸는지 password가 틀렸는지 구체적으로 알려주지 않는다.
    """

    # username으로 사용자 조회
    user = get_user_by_username(db, user_login.username)

    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="아이디 또는 비밀번호가 올바르지 않습니다.",
        )

    # 입력 비밀번호와 DB의 해시 비밀번호 비교
    if not verify_password(user_login.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="아이디 또는 비밀번호가 올바르지 않습니다.",
        )

    # JWT access token 발급
    #
    # sub에는 사용자를 식별할 수 있는 user.id를 넣는다.
    # username은 디버깅 또는 클라이언트 표시용으로 함께 넣을 수 있다.
    access_token = create_access_token(
        data={
            "sub": str(user.id),
            "username": user.username,
        }
    )

    return access_token