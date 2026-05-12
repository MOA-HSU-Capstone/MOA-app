"""
user_schema.py

사용자 관련 Pydantic 스키마

역할
- 회원가입 요청 데이터 검증
- 로그인 요청 데이터 검증
- 사용자 응답 데이터 형식 정의
- JWT 토큰 응답 형식 정의

현재 로그인 방식
- email 사용 X
- username을 로그인 ID로 사용
"""

from pydantic import BaseModel


class UserCreate(BaseModel):
    """
    회원가입 요청 스키마

    클라이언트가 회원가입할 때 보내는 데이터

    예시 요청
    --------
    {
        "username": "testuser",
        "password": "1234"
    }
    """

    # 로그인에 사용할 사용자 ID
    # DB에서는 unique=True로 중복을 막아야 한다.
    username: str

    # 사용자가 입력한 원문 비밀번호
    # 실제 DB에는 이 값이 그대로 저장되지 않고,
    # 해시 처리된 hashed_password가 저장된다.
    password: str


class UserLogin(BaseModel):
    """
    로그인 요청 스키마

    username과 password로 로그인한다.

    예시 요청
    --------
    {
        "username": "testuser",
        "password": "1234"
    }
    """

    # 로그인에 사용할 사용자 ID
    username: str

    # 사용자가 입력한 원문 비밀번호
    password: str


class UserResponse(BaseModel):
    """
    사용자 응답 스키마

    회원가입 성공 또는 사용자 정보 조회 시 반환할 데이터

    비밀번호와 hashed_password는 절대 응답에 포함하지 않는다.
    """

    # 사용자 고유 ID
    id: int

    # 로그인에 사용하는 사용자 ID
    username: str

    class Config:
        # SQLAlchemy ORM 객체를 Pydantic 응답 모델로 변환할 수 있게 함
        from_attributes = True


class TokenResponse(BaseModel):
    """
    로그인 성공 시 반환하는 JWT 토큰 응답 스키마

    예시 응답
    --------
    {
        "access_token": "jwt_token_value",
        "token_type": "bearer"
    }
    """

    # 발급된 JWT access token
    access_token: str

    # 토큰 타입
    # 일반적으로 Bearer 인증 방식을 사용하므로 기본값은 "bearer"
    token_type: str = "bearer"