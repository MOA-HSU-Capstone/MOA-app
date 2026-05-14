"""
main.py

애플리케이션 실행 진입점

역할
- FastAPI 앱 생성
- 라우터 등록
- DB 테이블 생성
- 기본 업로드 폴더 생성
- 기본 헬스체크 엔드포인트 제공

실행 예시
---------
python -m uvicorn main:app --reload
uvicorn main:app --reload
"""

from __future__ import annotations

from fastapi import FastAPI

from config.database import engine
from config.schema_compat import ensure_schema_compatibility
from models import Base
from routers import (
    auth_router,
    meeting_router,
    upload_router,
    decision_router,
    action_item_router,
)
from storage.upload_paths import ensure_base_upload_dirs


# -----------------------------------------
# FastAPI 앱 생성
# -----------------------------------------
app = FastAPI(
    title="MOA Meeting Assistant API",
    description=(
        "회의 오디오/이미지 업로드, transcript 저장, "
        "summary 생성, 회원가입/로그인 API"
    ),
    version="1.0.0",
)


# -----------------------------------------
# 앱 시작 시 실행되는 초기화 로직
# -----------------------------------------
@app.on_event("startup")
def on_startup() -> None:
    """
    애플리케이션 시작 시 초기화 작업 수행

    처리 내용
    -------
    1. DB 테이블 생성
    2. 기본 업로드 디렉토리 생성

    주의
    ----
    - 이미 존재하는 테이블은 다시 생성하지 않는다.
    - 이미 존재하는 폴더는 다시 생성하지 않는다.
    - User, Meeting, Transcript, Image, Summary 모델이
      Base에 등록되어 있어야 테이블이 생성된다.
    - 따라서 models/__init__.py에서 각 모델을 import하고 있어야 한다.
    """

    # SQLAlchemy Base에 등록된 모든 테이블 생성
    Base.metadata.create_all(bind=engine)
    ensure_schema_compatibility(engine)

    # 기본 업로드 폴더 생성
    #
    # 예시:
    # uploads/
    # └─ users/
    #
    # user_id, meeting_id별 세부 폴더는
    # 실제 파일 업로드 시점에 생성한다.
    ensure_base_upload_dirs()


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

# 인증 관련 API
# - POST /auth/register
# - POST /auth/login
app.include_router(auth_router)

# 회의 관련 API
# - 회의 생성
# - 회의 조회
# - 요약 생성/조회
# - 전체 transcript 조회
app.include_router(meeting_router)

# 업로드 관련 API
# - 오디오 업로드
# - 이미지 업로드
app.include_router(upload_router)

# 결정사항 관련 API
# - 결정사항 하나 추가
# - 결정사항 하나 수정
# - 결정사항 하나 삭제
app.include_router(decision_router)

# 할 일 관련 API
# - 할 일 하나 추가
# - 할 일 하나 수정
# - 할 일 하나 삭제
app.include_router(action_item_router)