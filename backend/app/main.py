"""
main.py

애플리케이션 실행 진입점

역할
- FastAPI 앱 생성
- 라우터 등록
- DB 테이블 생성
- 기본 헬스체크 엔드포인트 제공

실행 예시
---------
pythom -m uvicorn main:app --reload
uvicorn main:app --reload
"""
from __future__ import annotations

from fastapi import FastAPI

from config.database import engine
from models import Base
from routers import meeting_router, upload_router

# -----------------------------------------
# FastAPI 앱 생성
# -----------------------------------------
app = FastAPI(
    title="MOA Meeting Assistant API",
    description="회의 오디오/이미지 업로드, transcript 저장, summary 생성 API",
    version="1.0.0",
)


# -----------------------------------------
# 앱 시작 시 실행되는 초기화 로직
# -----------------------------------------
# DB 테이블 생성은 import 시점이 아니라
# 서버 시작 시점에 수행하는 편이 더 안전하다.
#
# 이유
# - 팀원이 실행할 때 DB 초기화 흐름을 더 명확하게 볼 수 있음
# - MySQL / SQLite fallback 구조와도 자연스럽게 맞음
# - 앱 import만 했을 때 곧바로 DB 작업이 실행되는 것을 줄일 수 있음
@app.on_event("startup")
def on_startup() -> None:
    """
    애플리케이션 시작 시 초기화 작업 수행

    현재는 SQLAlchemy Base에 등록된 모든 테이블을 생성한다.
    이미 존재하는 테이블은 다시 생성하지 않는다.
    """

    Base.metadata.create_all(bind=engine)


# -----------------------------------------
# 기본 엔드포인트
# -----------------------------------------
@app.get("/", summary="루트 엔드포인트")
def read_root() -> dict:
    """
    기본 루트 엔드포인트

    서버가 실행 중인지 간단히 확인할 때 사용한다.
    """

    return {
        "message": "MOA Meeting Assistant API is running."
    }


@app.get("/health", summary="헬스 체크")
def health_check() -> dict:
    """
    서버 상태 확인용 엔드포인트
    """

    return {
        "status": "ok"
    }


# -----------------------------------------
# 라우터 등록
# -----------------------------------------
app.include_router(meeting_router)
app.include_router(upload_router)