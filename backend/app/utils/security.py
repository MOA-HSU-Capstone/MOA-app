"""
security.py

보안 관련 유틸 함수

역할
- 비밀번호 해시 처리
- 비밀번호 검증
- JWT 토큰 생성
"""

from datetime import datetime, timedelta, timezone
from typing import Optional

from jose import jwt
from passlib.context import CryptContext

from config.settings import settings


# bcrypt 알고리즘을 사용해서 비밀번호를 안전하게 해시 처리
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(password: str) -> str:
    """
    사용자가 입력한 비밀번호를 해시값으로 변환

    DB에는 원문 비밀번호를 저장하면 안 된다.
    반드시 해시된 비밀번호를 저장해야 한다.
    """

    return pwd_context.hash(password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    로그인 시 입력한 비밀번호와 DB에 저장된 해시 비밀번호를 비교
    """

    return pwd_context.verify(plain_password, hashed_password)


def create_access_token(
    data: dict,
    expires_delta: Optional[timedelta] = None,
) -> str:
    """
    JWT access token 생성

    data에는 보통 사용자 id 또는 email을 넣는다.
    예:
    {
        "sub": "1"
    }
    """

    to_encode = data.copy()

    if expires_delta is None:
        expires_delta = timedelta(minutes=settings.access_token_expire_minutes)

    expire = datetime.now(timezone.utc) + expires_delta

    to_encode.update({"exp": expire})

    encoded_jwt = jwt.encode(
        to_encode,
        settings.jwt_secret_key,
        algorithm=settings.jwt_algorithm,
    )

    return encoded_jwt