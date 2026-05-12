# 설정 관리(placeholder).
# TODO:
# - 환경변수 로딩 및 검증(OPENAI_API_KEY, OPENAI_MODEL 등)
# - python-dotenv/pydantic-settings 도입 검토
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
    # 예: http://34.64.93.14
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
    # SECRET_KEY
    # - JWT 토큰 서명에 사용되는 비밀키
    # - 실제 배포 환경에서는 반드시 긴 랜덤 문자열로 설정해야 한다.
    #
    # ALGORITHM
    # - JWT 암호화 알고리즘
    # - 일반적으로 HS256 사용
    #
    # ACCESS_TOKEN_EXPIRE_MINUTES
    # - access token 만료 시간
    # - 1440분 = 24시간
    # - 10080분 = 7일
    # - 43200분 = 30일
    secret_key: str = "change-this-secret-key"
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 1440


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
    """

    value = os.getenv(key, default)

    if required and not value:
        raise ValueError(f"{key} 환경변수가 설정되지 않았습니다.")

    return value


def load_settings() -> Settings:
    """
    환경변수에서 Settings 객체를 생성

    Returns
    -------
    Settings
        로딩된 설정 객체
    """

    return Settings(
        # OpenAI
        openai_api_key=get_env("OPENAI_API_KEY"),
        openai_model=get_env("OPENAI_MODEL", "gpt-4o-mini") or "gpt-4o-mini",

        # DB 공통
        db_type=(get_env("DB_TYPE", "sqlite") or "sqlite").lower(),

        # MySQL
        # required=True 를 제거한 이유:
        # 팀원이 MySQL을 사용하지 않아도 SQLite로 실행할 수 있게 하기 위함
        db_user=get_env("DB_USER"),
        db_password=get_env("DB_PASSWORD"),
        db_host=get_env("DB_HOST", "localhost") or "localhost",
        db_port=int(get_env("DB_PORT", "3306") or "3306"),
        db_name=get_env("DB_NAME"),

        # SQLite
        sqlite_path=get_env("SQLITE_PATH", "./local_dev.db") or "./local_dev.db",

        # STT 서버
        # 필수로 두면 팀원 환경에서 서버 실행이 막힐 수 있으므로 optional 처리
        stt_server_url=get_env("STT_SERVER_URL"),

        # 저장 경로
        upload_dir=get_env("UPLOAD_DIR", "uploads") or "uploads",
        audio_dir=get_env("AUDIO_DIR", "audio") or "audio",
        image_dir=get_env("IMAGE_DIR", "images") or "images",

        # JWT 인증
        secret_key=get_env("SECRET_KEY", "change-this-secret-key")
        or "change-this-secret-key",
        algorithm=get_env("ALGORITHM", "HS256") or "HS256",
        access_token_expire_minutes=int(
            get_env("ACCESS_TOKEN_EXPIRE_MINUTES", "1440") or "1440"
        ),
    )


# -----------------------------------------
# 전역 설정 객체
# -----------------------------------------
# 프로젝트 전체에서 공통으로 사용한다.
settings = load_settings()