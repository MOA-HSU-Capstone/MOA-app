"""
settings.py

환경변수 설정 관리
"""

import os
from dataclasses import dataclass
from dotenv import load_dotenv


load_dotenv()


@dataclass
class Settings:
    # OpenAI
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

    # STT
    stt_server_url: str = os.getenv("STT_SERVER_URL", "")

    # Upload
    upload_dir: str = os.getenv("UPLOAD_DIR", "uploads")
    audio_dir: str = os.getenv("AUDIO_DIR", "audio")
    image_dir: str = os.getenv("IMAGE_DIR", "images")

    # DB
    db_type: str = os.getenv("DB_TYPE", "sqlite")
    sqlite_path: str = os.getenv("SQLITE_PATH", "./local_dev.db")

    db_user: str = os.getenv("DB_USER", "")
    db_password: str = os.getenv("DB_PASSWORD", "")
    db_host: str = os.getenv("DB_HOST", "localhost")
    db_port: str = os.getenv("DB_PORT", "3306")
    db_name: str = os.getenv("DB_NAME", "")

    # JWT
    # .env에서 SECRET_KEY를 쓰든 JWT_SECRET_KEY를 쓰든 둘 다 인식되게 처리
    jwt_secret_key: str = os.getenv(
        "JWT_SECRET_KEY",
        os.getenv("SECRET_KEY", "dev-secret-key"),
    )

    # .env에서 ALGORITHM을 쓰든 JWT_ALGORITHM을 쓰든 둘 다 인식되게 처리
    jwt_algorithm: str = os.getenv(
        "JWT_ALGORITHM",
        os.getenv("ALGORITHM", "HS256"),
    )

    access_token_expire_minutes: int = int(
        os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", "60")
    )


settings = Settings()