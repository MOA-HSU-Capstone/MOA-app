"""
utils/domain_terms.py

역할
- STT 결과에서 자주 잘못 인식되는 도메인 용어를 보정한다.
- 기술 용어, 학교/과제 용어, 회의록 앱 용어처럼 특정 분야에 의존적인 보정만 담당한다.
- 공백 정리, 반복어 제거, 기한/할 일 후보 표시 같은 공통 후처리는 stt_postprocess.py에서 처리한다.

사용 예시
-------
text = apply_domain_terms(
    text=transcript,
    domains=["it", "school", "meeting"],
)

주의
----
- 도메인 용어 보정은 특정 분야에 치우칠 수 있으므로 항상 필요한 도메인만 선택해서 적용한다.
- 사람 이름은 여기서 보정하지 않는다.
- 사람 이름은 참석자 목록 기반으로 stt_postprocess.py에서 처리한다.
"""

from __future__ import annotations


DOMAIN_REPLACEMENTS: dict[str, dict[str, str]] = {
    "it": {
        # API
        "에이피아이": "API",
        "에이피 아이": "API",
        "아피": "API",
        "아피들": "API들",
        "API 들": "API들",

        # FastAPI
        "페스트 API": "FastAPI",
        "페스트API": "FastAPI",
        "패스트 API": "FastAPI",
        "패스트API": "FastAPI",
        "파스트 API": "FastAPI",
        "Fast API": "FastAPI",
        "fast API": "FastAPI",
        "패스트파이": "FastAPI",

        # STT / OCR / LLM
        "에스티티": "STT",
        "오씨알": "OCR",
        "오시알": "OCR",
        "오시아": "OCR",
        "엘엘엠": "LLM",
        "엘렘": "LLM",
        "엘 엘 엠": "LLM",

        # AI
        "에이아이": "AI",
        "AI 비주얼": "AI 비중",
        "AI 비율": "AI 비중",

        # Backend / Frontend
        "백앤드": "백엔드",
        "백앤": "백엔드",
        "뱀견드": "백엔드",
        "뱅견드": "백엔드",
        "프론트 앤드": "프론트엔드",
        "프론트앤드": "프론트엔드",

        # Database
        "데이터 베이스": "데이터베이스",
        "디비": "DB",
        "디비 서버": "DB 서버",
        "디비 파일": "DB 파일",
        "디비패": "DB 파일",
        "디비 접근": "DB 접근",

        # SQLite
        "에스큐라이트": "SQLite",
        "에스큐엘라이트": "SQLite",
        "스큐라이트": "SQLite",
        "스퀄라이트": "SQLite",
        "SQL 라이트": "SQLite",
        "SQL라이트": "SQLite",
        "SKU 라이트": "SQLite",
        "SKU의 라이트": "SQLite",

        # SQLAlchemy / ORM
        "SQL 알캐미": "SQLAlchemy",
        "SQL 알캐니": "SQLAlchemy",
        "SQL 알케미": "SQLAlchemy",
        "스큐엘알케미": "SQLAlchemy",
        "에스큐엘알케미": "SQLAlchemy",
        "알캐미": "SQLAlchemy",
        "알캐니": "SQLAlchemy",
        "알케미": "SQLAlchemy",
        "오알엠": "ORM",
        "OARM": "ORM",
        "O 알 M": "ORM",

        # MySQL / PostgreSQL
        "마이 SQL": "MySQL",
        "마이에스큐엘": "MySQL",
        "마이 에스큐엘": "MySQL",
        "마이 SKU": "MySQL",
        "마의 SKU": "MySQL",
        "포스트그레스": "PostgreSQL",
        "포스트그레 SQL": "PostgreSQL",
        "포스트그레스큐엘": "PostgreSQL",
        "포스트의 SKU": "PostgreSQL",

        # CPU / GPU
        "씨피유": "CPU",
        "시피유": "CPU",
        "씨피오": "CPU",
        "CPO": "CPU",
        "지피유": "GPU",
        "지피오": "GPU",
        "GPO": "GPU",
        "그래픽 카드": "그래픽카드",

        # Dev tools / frameworks
        "깃 허브": "GitHub",
        "깃허브": "GitHub",
        "스웨거": "Swagger",
        "레트로핏": "Retrofit",
        "유비콘": "Uvicorn",
        "유비콘 서버": "Uvicorn 서버",
        "파이댄틱": "Pydantic",
        "파이던틱": "Pydantic",

        # Server / deployment
        "노헙": "nohup",
        "엔진엑스": "Nginx",
        "도커": "Docker",
        "우분투": "Ubuntu",

        # Auth
        "제이더블유티": "JWT",
        "제이 더블유 티": "JWT",
        "엑세스 토큰": "access token",
        "액세스 토큰": "access token",
        "베어러 토큰": "Bearer token",
    },

    "school": {
        # 학교/과제/발표
        "캡스틱": "캡스톤",
        "캡스톤은 전시회": "캡스톤 전시회",
        "캡스톤 전시의": "캡스톤 전시회",
        "전시의 발표": "전시회 발표",
        "발표의 예상 질문": "발표 예상 질문",
        "예상질문": "예상 질문",

        "과재": "과제",
        "팀플": "팀 프로젝트",
        "팀플젝": "팀 프로젝트",
        "발표 자료": "발표자료",
        "제출 자료": "제출자료",
        "최종 발표": "최종발표",
        "중간 발표": "중간발표",
        "최종 보고서": "최종보고서",
        "중간 보고서": "중간보고서",
        "실습 자료": "실습자료",

        # 교수/수업
        "교수님들이 생각하기에는": "교수님들이 생각하시기에는",
        "수업자료": "수업자료",
        "강의자료": "강의자료",
    },

    "meeting": {
        # 회의록 앱에서 자주 쓰는 용어
        "회의 록": "회의록",
        "회의록 앱": "회의록 앱",
        "회의 요약": "회의 요약",
        "요약 생성": "요약 생성",
        "결정 사항": "결정사항",
        "액션아이템": "action item",
        "액션 아이템": "action item",
        "할일": "할 일",
        "할 일 목록": "할 일 목록",
        "담당 자": "담당자",
        "기한 일": "기한일",

        # 문서/OCR 관련
        "피디에프": "PDF",
        "피디에프 파일": "PDF 파일",
        "문서 분석": "문서 분석",
        "이미지 분석": "이미지 분석",

        # MOA 회의록 앱 관련
        "모아 앱": "MOA 앱",
        "모아 프로젝트": "MOA 프로젝트",
        "음성 회의록": "음성 회의록",
        "AI 회의록": "AI 회의록",
        "회의록 생성": "회의록 생성",
        "녹음 파일": "녹음파일",
        "사진 파일": "사진파일",
        "파일 조회": "파일 조회",
        "파일 다운로드": "파일 다운로드",
        "폴더 구조": "폴더 구조",
    },
}


def _replace_terms(text: str, replacements: dict[str, str]) -> str:
    """
    단순 문자열 치환을 수행한다.

    긴 표현부터 먼저 치환해서
    짧은 표현이 먼저 바뀌며 생기는 충돌을 줄인다.
    """

    for wrong, correct in sorted(
        replacements.items(),
        key=lambda item: len(item[0]),
        reverse=True,
    ):
        text = text.replace(wrong, correct)

    return text


def apply_domain_terms(
    text: str,
    domains: list[str] | None = None,
) -> str:
    """
    선택한 도메인에 해당하는 용어 보정을 적용한다.

    Parameters
    ----------
    text:
        STT transcript 문자열

    domains:
        적용할 도메인 목록.
        None 또는 빈 리스트이면 아무 보정도 하지 않는다.

        사용 가능한 값:
        - "it"
        - "school"
        - "meeting"

    Returns
    -------
    str
        도메인 용어가 보정된 transcript
    """

    if not text:
        return ""

    if not domains:
        return text

    for domain in domains:
        replacements = DOMAIN_REPLACEMENTS.get(domain)

        if replacements:
            text = _replace_terms(text, replacements)

    return text


def get_available_domains() -> list[str]:
    """
    사용 가능한 도메인 이름 목록을 반환한다.
    """

    return list(DOMAIN_REPLACEMENTS.keys())


def add_domain_terms(
    domain: str,
    replacements: dict[str, str],
) -> None:
    """
    런타임에서 도메인 용어를 추가한다.

    예:
    add_domain_terms(
        domain="custom",
        replacements={
            "잘못 인식된 말": "올바른 용어",
        },
    )

    주의
    ----
    - 서버 실행 중 메모리에만 반영된다.
    - 영구 저장이 필요하면 이 파일에 직접 추가해야 한다.
    """

    if not domain:
        return

    if domain not in DOMAIN_REPLACEMENTS:
        DOMAIN_REPLACEMENTS[domain] = {}

    DOMAIN_REPLACEMENTS[domain].update(replacements)