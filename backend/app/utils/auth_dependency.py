"""
auth_dependency.py

인증 의존성 모듈

역할
- 요청 헤더의 JWT access token 확인
- 토큰에서 현재 로그인한 사용자 ID 추출
- DB에서 현재 사용자 조회
- 보호된 API에서 current_user를 사용할 수 있게 제공

요청 헤더 예시
-------------
Authorization: Bearer {access_token}
"""

from __future__ import annotations

from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt
from sqlalchemy.orm import Session

from config.database import get_db
from config.settings import settings
from models.user_model import User


# Swagger UI에서 Authorize 버튼을 통해 Bearer 토큰을 입력할 수 있게 해준다.
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")


def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: Session = Depends(get_db),
) -> User:
    """
    JWT 토큰을 확인하고 현재 로그인한 사용자 정보를 반환한다.

    처리 과정
    -------
    1. Authorization 헤더에서 Bearer token 추출
    2. JWT 토큰 복호화
    3. payload에서 sub 값 추출
    4. sub 값을 user_id로 변환
    5. DB에서 User 조회
    6. User 객체 반환

    사용 예시
    --------
    current_user: User = Depends(get_current_user)
    """

    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="인증 정보가 올바르지 않습니다.",
        headers={"WWW-Authenticate": "Bearer"},
    )

    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret_key,
            algorithms=[settings.jwt_algorithm],
        )

        user_id = payload.get("sub")

        if user_id is None:
            raise credentials_exception

    except JWTError:
        raise credentials_exception

    try:
        user_id_int = int(user_id)
    except ValueError:
        raise credentials_exception

    user = (
        db.query(User)
        .filter(User.id == user_id_int)
        .first()
    )

    if user is None:
        raise credentials_exception

    return user