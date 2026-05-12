"""
auth_router.py

회원가입 / 로그인 API 라우터

역할
- /auth/register
- /auth/login

현재 인증 방식
- email 사용 X
- username + password 사용

주의
- /docs 오른쪽 위 Authorize 버튼을 사용하려면
  /auth/login은 JSON이 아니라 form 방식으로 username/password를 받아야 한다.
"""

from fastapi import APIRouter, Depends, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session

from config.database import get_db
from schemas.user_schema import UserCreate, UserLogin, UserResponse, TokenResponse
from services.auth_service import register_user, login_user


router = APIRouter(
    prefix="/auth",
    tags=["Auth"],
)


@router.post(
    "/register",
    response_model=UserResponse,
    status_code=status.HTTP_201_CREATED,
)
def register(
    user_create: UserCreate,
    db: Session = Depends(get_db),
):
    """
    회원가입 API

    요청 예시
    --------
    {
        "username": "testuser",
        "password": "1234"
    }

    응답 예시
    --------
    {
        "id": 1,
        "username": "testuser"
    }

    주의
    ----
    - email은 사용하지 않는다.
    - password는 응답에 포함하지 않는다.
    - password는 service 계층에서 해시 처리 후 DB에 저장된다.
    """

    user = register_user(db, user_create)

    return user


@router.post(
    "/login",
    response_model=TokenResponse,
)
def login(
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: Session = Depends(get_db),
):
    """
    로그인 API

    /docs 오른쪽 위 Authorize 버튼에서 사용하는 로그인 API

    요청 방식
    --------
    JSON이 아니라 application/x-www-form-urlencoded 방식으로 받는다.

    Swagger Authorize 입력
    ---------------------
    username: 회원가입한 아이디
    password: 회원가입한 비밀번호
    client_id: 비워두기
    client_secret: 비워두기

    응답 예시
    --------
    {
        "access_token": "...",
        "token_type": "bearer"
    }

    사용 방법
    --------
    로그인 성공 후 받은 access_token을 이후 API 요청에 사용한다.

    예시
    ----
    Authorization: Bearer {access_token}
    """

    # OAuth2PasswordRequestForm으로 받은 form 데이터를
    # 기존 service 계층에서 사용하던 UserLogin 스키마 형태로 변환한다.
    user_login = UserLogin(
        username=form_data.username,
        password=form_data.password,
    )

    access_token = login_user(db, user_login)

    return TokenResponse(access_token=access_token)