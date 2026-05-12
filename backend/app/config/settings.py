"""
settings.py

환경변수 설정 관리

역할
- .env 파일 자동 로딩
- OpenAI 설정 관리
- DB 설정 관리
- 외부 STT 서버 URL 관리
- 업로드 파일 저장 경로 관리
- JWT 인증 설정 관리
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Optional

from dotenv import load_dotenv


# -----------------------------------------
# .env 파일 자동 로딩
# -----------------------------------------
# 프로젝트 루트의 .env 파일을 읽는다.
# .env가 없더라도, 운영체제 환경변수만으로도 동작할 수 있다.
load_dotenv()


@dataclass(frozen=True)
class Settings:
    """
    애플리케이션 설정 데이터 객체
    """

    # -------------------------
    # OpenAI 설정
    # -------------------------
    openai_api_key: Optional[str] = None
    openai_model: str = "gpt-4o-mini"

    # -------------------------
    # DB 공통 설정
    # -------------------------
    # mysql 또는 sqlite
    # 기본값은 sqlite로 두어,
    # 팀원이 MySQL이 없는 환경이어도 바로 실행 가능하게 한다.
    db_type: str = "sqlite"

    # -------------------------
    # MySQL 설정
    # -------------------------
    db_user: Optional[str] = None
    db_password: Optional[str] = None
    db_host: str = "localhost"
    db_port: int = 3306
    db_name: Optional[str] = None

    # -------------------------
    # SQLite 설정
    # -------------------------
    # 예: ./local_dev.db
    sqlite_path: str = "./local_dev.db"

    # -------------------------
    # 외부 STT 서버 설정
    # 예: http://34.64.93.14:5000
    # 주의: 보통 /transcribe는 붙이지 않고 기본 URL만 넣는다.
    # -------------------------
    stt_server_url: Optional[str] = None

    # -------------------------
    # 파일 저장 경로 설정
    # -------------------------
    upload_dir: str = "uploads"
    audio_dir: str = "audio"
    image_dir: str = "images"

    # -------------------------
    # JWT 인증 설정
    # -------------------------
    # access token 생성/검증에 사용
    jwt_secret_key: str = "dev-secret-key"

    # JWT 알고리즘
    jwt_algorithm: str = "HS256"

    # access token 만료 시간, 단위: 분
    access_token_expire_minutes: int = 60


def get_env(
    key: str,
    default: Optional[str] = None,
    *,
    required: bool = False,
) -> Optional[str]:
    """
    환경변수를 읽는 유틸리티 함수

    Parameters
    ----------
    key : str
        환경변수 이름

    default : Optional[str]
        기본값

    required : bool
        필수 여부

    Returns
    -------
    Optional[str]
        환경변수 값 또는 기본값
    """

    value = os.getenv(key, default)

    if required and not value:
        raise ValueError(f"{key} 환경변수가 설정되지 않았습니다.")

    return value


def get_int_env(
    key: str,
    default: int,
    *,
    required: bool = False,
) -> int:
    """
    정수형 환경변수를 읽는 유틸리티 함수

    예
    --
    DB_PORT=3306
    ACCESS_TOKEN_EXPIRE_MINUTES=1440
    """

    value = get_env(
        key=key,
        default=str(default),
        required=required,
    )

    try:
        return int(value or default)
    except ValueError:
        raise ValueError(f"{key} 환경변수는 정수여야 합니다. 현재 값: {value}")


def load_settings() -> Settings:
    """
    환경변수에서 Settings 객체를 생성

    Returns
    -------
    Settings
        로딩된 설정 객체
    """

    return Settings(
        # -------------------------
        # OpenAI
        # -------------------------
        openai_api_key=get_env("OPENAI_API_KEY"),
        openai_model=get_env("OPENAI_MODEL", "gpt-4o-mini") or "gpt-4o-mini",

        # -------------------------
        # DB 공통
        # -------------------------
        db_type=(get_env("DB_TYPE", "sqlite") or "sqlite").lower(),

        # -------------------------
        # MySQL
        # -------------------------
        # required=True 를 제거한 이유:
        # 팀원이 MySQL을 사용하지 않아도 SQLite로 실행할 수 있게 하기 위함
        db_user=get_env("DB_USER"),
        db_password=get_env("DB_PASSWORD"),
        db_host=get_env("DB_HOST", "localhost") or "localhost",
        db_port=get_int_env("DB_PORT", 3306),
        db_name=get_env("DB_NAME"),

        # -------------------------
        # SQLite
        # -------------------------
        sqlite_path=get_env("SQLITE_PATH", "./local_dev.db") or "./local_dev.db",

        # -------------------------
        # STT 서버
        # -------------------------
        # 필수로 두면 팀원 환경에서 서버 실행이 막힐 수 있으므로 optional 처리
        stt_server_url=get_env("STT_SERVER_URL"),

        # -------------------------
        # 저장 경로
        # -------------------------
        upload_dir=get_env("UPLOAD_DIR", "uploads") or "uploads",
        audio_dir=get_env("AUDIO_DIR", "audio") or "audio",
        image_dir=get_env("IMAGE_DIR", "images") or "images",

        # -------------------------
        # JWT 인증
        # -------------------------
        # 네 .env에서 SECRET_KEY를 쓰고 있어도 인식되게 처리
        # JWT_SECRET_KEY를 쓰는 경우도 같이 지원
        jwt_secret_key=(
            get_env("JWT_SECRET_KEY")
            or get_env("SECRET_KEY")
            or "dev-secret-key"
        ),

        # 네 .env에서 ALGORITHM을 쓰고 있어도 인식되게 처리
        # JWT_ALGORITHM을 쓰는 경우도 같이 지원
        jwt_algorithm=(
            get_env("JWT_ALGORITHM")
            or get_env("ALGORITHM")
            or "HS256"
        ),

        access_token_expire_minutes=get_int_env(
            "ACCESS_TOKEN_EXPIRE_MINUTES",
            60,
        ),
    )


# -----------------------------------------
# 전역 설정 객체
# -----------------------------------------
# 프로젝트 전체에서 공통으로 사용한다.
settings = load_settings()