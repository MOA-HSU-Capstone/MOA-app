from __future__ import annotations

from typing import Generator

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session, sessionmaker

from .settings import Settings, settings


def build_database_url(
    *,
    db_user: str,
    db_password: str,
    db_host: str,
    db_port: int,
    db_name: str,
    driver: str = "mysql+pymysql",
) -> str:
    """
    MySQL 데이터베이스 연결 URL을 생성합니다.

    Parameters
    ----------
    db_user : str
        DB 사용자명

    db_password : str
        DB 비밀번호

    db_host : str
        DB 호스트

    db_port : int
        DB 포트

    db_name : str
        DB 이름

    driver : str
        SQLAlchemy 드라이버 문자열
    """

    return (
        f"{driver}://"
        f"{db_user}:"
        f"{db_password}@"
        f"{db_host}:"
        f"{db_port}/"
        f"{db_name}"
    )


def build_sqlite_url(sqlite_path: str) -> str:
    """
    SQLite 데이터베이스 연결 URL을 생성합니다.

    Parameters
    ----------
    sqlite_path : str
        SQLite 파일 경로
    """

    return f"sqlite:///{sqlite_path}"


def build_database_url_from_settings(settings: Settings) -> str:
    """
    Settings 객체를 사용해 DB URL을 생성합니다.

    DB_TYPE 값에 따라 MySQL 또는 SQLite URL을 반환합니다.

    Parameters
    ----------
    settings : Settings
        설정 객체
    """

    db_type = settings.db_type.lower()

    if db_type == "sqlite":
        return build_sqlite_url(settings.sqlite_path)

    if db_type == "mysql":
        if not settings.db_user:
            raise ValueError("DB_TYPE=mysql 인데 DB_USER 설정이 없습니다.")

        if not settings.db_password:
            raise ValueError("DB_TYPE=mysql 인데 DB_PASSWORD 설정이 없습니다.")

        if not settings.db_host:
            raise ValueError("DB_TYPE=mysql 인데 DB_HOST 설정이 없습니다.")

        if not settings.db_name:
            raise ValueError("DB_TYPE=mysql 인데 DB_NAME 설정이 없습니다.")

        return build_database_url(
            db_user=settings.db_user,
            db_password=settings.db_password,
            db_host=settings.db_host,
            db_port=settings.db_port,
            db_name=settings.db_name,
        )

    raise ValueError(f"지원하지 않는 DB_TYPE입니다: {settings.db_type}")


def create_db_engine(
    database_url: str,
    *,
    pool_pre_ping: bool = True,
    echo: bool = False,
) -> Engine:
    """
    SQLAlchemy engine을 생성합니다.

    SQLite와 MySQL은 필요한 옵션이 조금 다르므로 분기 처리합니다.

    Parameters
    ----------
    database_url : str
        데이터베이스 연결 URL

    pool_pre_ping : bool
        연결 상태 확인 옵션

    echo : bool
        SQL 로그 출력 여부
    """

    if database_url.startswith("sqlite"):
        return create_engine(
            database_url,
            connect_args={"check_same_thread": False},
            echo=echo,
        )

    return create_engine(
        database_url,
        pool_pre_ping=pool_pre_ping,
        echo=echo,
    )


def create_session_factory(engine: Engine) -> sessionmaker:
    """
    sessionmaker(SessionLocal)를 생성합니다.

    Parameters
    ----------
    engine : Engine
        SQLAlchemy engine
    """

    return sessionmaker(
        autocommit=False,
        autoflush=False,
        bind=engine,
    )


def create_get_db(session_factory: sessionmaker):
    """
    FastAPI Depends용 get_db 함수를 생성합니다.

    Parameters
    ----------
    session_factory : sessionmaker
        SessionLocal 객체
    """

    def get_db() -> Generator[Session, None, None]:
        db = session_factory()
        try:
            yield db
        finally:
            db.close()

    return get_db


def test_database_connection(engine: Engine) -> None:
    """
    실제 DB 연결이 가능한지 간단한 쿼리로 확인합니다.

    연결이 안 되면 예외를 발생시킵니다.
    """

    with engine.connect() as connection:
        connection.execute(text("SELECT 1"))


def initialize_database(
    settings: Settings,
    *,
    echo: bool = False,
) -> tuple[str, Engine, sessionmaker]:
    """
    데이터베이스를 초기화합니다.

    동작 방식
    --------
    1. DB_TYPE=sqlite 이면 SQLite로 바로 연결
    2. DB_TYPE=mysql 이면 MySQL 연결 시도
    3. MySQL 연결 실패 시 SQLite로 자동 fallback

    Returns
    -------
    tuple[str, Engine, sessionmaker]
        DATABASE_URL, engine, SessionLocal
    """

    primary_database_url = build_database_url_from_settings(settings)

    # -----------------------------------------
    # 1차: 설정된 DB로 먼저 연결 시도
    # -----------------------------------------
    try:
        primary_engine = create_db_engine(
            primary_database_url,
            echo=echo,
        )

        # 실제 접속 가능 여부 확인
        test_database_connection(primary_engine)

        print(f"[DB] {settings.db_type.upper()} 연결 성공")
        return (
            primary_database_url,
            primary_engine,
            create_session_factory(primary_engine),
        )

    except (SQLAlchemyError, ValueError, OSError) as exc:
        # SQLite로 지정한 상태에서 실패했다면 그대로 예외를 올린다.
        # 이 경우는 SQLite 경로 문제 등으로 보는 것이 맞다.
        if settings.db_type.lower() == "sqlite":
            raise RuntimeError(
                f"SQLite 연결에 실패했습니다: {exc}"
            ) from exc

        # MySQL 실패 시에만 SQLite fallback 수행
        print(f"[DB] MySQL 연결 실패: {exc}")
        print("[DB] SQLite fallback 으로 전환합니다.")

    # -----------------------------------------
    # 2차: SQLite fallback
    # -----------------------------------------
    fallback_database_url = build_sqlite_url(settings.sqlite_path)
    fallback_engine = create_db_engine(
        fallback_database_url,
        echo=echo,
    )

    # SQLite는 파일 기반 DB라 보통 바로 생성/연결 가능
    test_database_connection(fallback_engine)

    print(f"[DB] SQLITE 연결 성공: {settings.sqlite_path}")
    return (
        fallback_database_url,
        fallback_engine,
        create_session_factory(fallback_engine),
    )


# -----------------------------------------
# 실제 사용 객체 생성
# -----------------------------------------
# DB_TYPE=mysql 이어도 접속 실패하면 자동으로 SQLite로 전환된다.
DATABASE_URL, engine, SessionLocal = initialize_database(settings)

# FastAPI Depends에서 사용할 get_db 함수
get_db = create_get_db(SessionLocal)