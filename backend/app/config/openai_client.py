"""
openai_client.py

OpenAI 클라이언트 생성 및 재사용 파일

역할
- settings에서 OpenAI API Key를 읽음
- 기본 OpenAI 클라이언트를 1회 생성
- 프로젝트 전체에서 공통으로 재사용

동작 방식
- api_key를 직접 넘기면 해당 키로 새 클라이언트 생성
- api_key를 넘기지 않으면 기본 싱글톤 클라이언트 반환
"""

from __future__ import annotations

from openai import OpenAI

from config.settings import settings


# -----------------------------------------
# 기본 OpenAI 클라이언트 생성
# -----------------------------------------
# 기본 키가 없으면 OpenAI 기능을 쓸 수 없으므로 예외 처리
if not settings.openai_api_key:
    raise ValueError("OPENAI_API_KEY가 설정되지 않았습니다.")

_default_client = OpenAI(api_key=settings.openai_api_key)


def get_openai_client(api_key: str | None = None) -> OpenAI:
    """
    OpenAI 클라이언트를 반환

    Parameters
    ----------
    api_key : str | None
        별도로 사용할 API Key
        None이면 기본 싱글톤 클라이언트를 반환
    """

    # 별도 키를 넘기면 새 클라이언트를 생성
    if api_key:
        return OpenAI(api_key=api_key)

    # 기본은 전역 싱글톤 사용
    return _default_client