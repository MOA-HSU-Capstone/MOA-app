"""
meeting_summarizer.py

회의 요약 LLM 엔진

역할
- STT JSON / OCR JSON payload를 입력받아
  OpenAI API를 통해 구조화된 회의 요약 결과를 생성
- 반환 형식은 summary / decisions / action_items 만 포함한 dict

반환 예시
---------
{
    "summary": "...",
    "decisions": ["..."],
    "action_items": [
        {
            "task": "...",
            "owner": "...",
            "deadline": "..."
        }
    ]
}

특징
- OpenAI 클라이언트는 config/openai_client.py를 사용
- 모델명은 config/settings.py에서 읽음
- STT와 OCR은 LLM 입력에는 함께 사용하지만,
  반환은 최종 요약 결과만 제공
"""

from __future__ import annotations

import json
import logging
from typing import Any, Dict, Sequence

from config.openai_client import get_openai_client
from config.settings import settings
from utils.preprocess import stt_json_to_text

logger = logging.getLogger(__name__)


def _build_prompt(stt_text: str, ocr_text: str, title: str = "") -> str:
    """
    STT 텍스트 + OCR 텍스트를 LLM에 전달할 프롬프트 생성
    """

    stt_text = (stt_text or "").strip()
    ocr_text = (ocr_text or "").strip()
    title = (title or "").strip()

    return (
        "당신은 회의 내용을 구조화하는 분석가입니다.\n"
        "다음 입력(STT 발언 + OCR 메모)을 근거로 회의 결과를 추출하세요.\n"
        "\n"
        "규칙:\n"
        '- 출력은 오직 유효한 JSON만 허용합니다(마크다운/설명/코드펜스 금지).\n'
        '- 입력에 명시적으로 없는 내용은 절대 추측하거나 추가하지 마세요.\n'
        '- 알 수 없는 값은 빈 문자열 "" 또는 빈 리스트 []로 두세요.\n'
        "\n"
        "반환 JSON 형식:\n"
        "{\n"
        '  "summary": "...",\n'
        '  "decisions": ["..."],\n'
        '  "action_items": [\n'
        '    { "task": "...", "owner": "...", "deadline": "..." }\n'
        "  ]\n"
        "}\n"
        "\n"
        f"회의 제목: {title or '(없음)'}\n"
        "\n"
        "STT:\n"
        f"{stt_text}\n"
        "\n"
        "OCR:\n"
        f"{ocr_text}\n"
    )


def _build_payload_prompt(payload: Dict[str, Any]) -> str:
    """
    STT JSON + OCR JSON + 회의 메타데이터를 하나의 payload로 받아
    LLM에 전달할 프롬프트 생성
    """

    payload_json = json.dumps(payload, ensure_ascii=False, indent=2)

    return (
        "당신은 회의 내용을 구조화하는 분석가입니다.\n"
        "입력으로 회의 메타데이터, STT JSON, OCR/이미지 분석 JSON이 주어집니다.\n"
        "이 정보를 종합하여 회의 결과를 추출하세요.\n"
        "\n"
        "규칙:\n"
        '- 출력은 오직 유효한 JSON만 허용합니다(마크다운/설명/코드펜스 금지).\n'
        '- 입력에 명시적으로 없는 내용은 절대 추측하거나 추가하지 마세요.\n'
        '- 알 수 없는 값은 빈 문자열 "" 또는 빈 리스트 []로 두세요.\n'
        '- STT 내용과 OCR/이미지 분석 내용을 함께 참고하세요.\n'
        '- OCR/이미지 분석 내용은 회의 맥락 보강용 자료이므로, STT와 충돌하면 더 보수적으로 요약하세요.\n'
        "\n"
        "반환 JSON 형식:\n"
        "{\n"
        '  "summary": "...",\n'
        '  "decisions": ["..."],\n'
        '  "action_items": [\n'
        '    { "task": "...", "owner": "...", "deadline": "..." }\n'
        "  ]\n"
        "}\n"
        "\n"
        "입력 payload(JSON):\n"
        f"{payload_json}\n"
    )


def _extract_json_text(raw: str) -> str:
    """
    모델 응답에서 JSON 본문만 최대한 안전하게 추출
    """

    raw = (raw or "").strip()

    if raw.startswith("{") and raw.endswith("}"):
        return raw

    start = raw.find("{")
    end = raw.rfind("}")

    if start != -1 and end != -1 and end > start:
        return raw[start:end + 1]

    return raw


def _call_llm(prompt: str) -> Dict[str, Any]:
    """
    공통 OpenAI 호출 함수

    Returns
    -------
    Dict[str, Any]
        구조화된 회의 요약 결과
    """

    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")

    client = get_openai_client()

    try:
        response = client.chat.completions.create(
            model=settings.openai_model,
            temperature=0.2,
            response_format={"type": "json_object"},
            messages=[
                {
                    "role": "system",
                    "content": "반드시 유효한 JSON만 반환하세요.",
                },
                {
                    "role": "user",
                    "content": prompt,
                },
            ],
        )

        content = (response.choices[0].message.content or "").strip()

    except Exception as e:
        logger.exception("OpenAI API 호출 실패")
        raise RuntimeError(f"OpenAI API 호출에 실패했습니다: {e}") from e

    if not content:
        raise RuntimeError("OpenAI API가 빈 응답을 반환했습니다.")

    try:
        parsed = json.loads(_extract_json_text(content))
    except json.JSONDecodeError as e:
        raise RuntimeError(
            "모델 출력(JSON)을 파싱하지 못했습니다. "
            f"에러: {e}. 원본 출력(앞부분): {content[:3000]}"
        ) from e

    if not isinstance(parsed, dict):
        raise RuntimeError("모델 JSON의 최상위는 객체(dict)여야 합니다.")

    # 최소 필드 보정
    parsed.setdefault("summary", "")
    parsed.setdefault("decisions", [])
    parsed.setdefault("action_items", [])

    return parsed


def summarize_meeting_from_text(
    stt_text: str,
    ocr_text: str,
    title: str = "",
) -> Dict[str, Any]:
    """
    STT 텍스트 + OCR 텍스트를 기반으로 회의 요약 수행

    Returns
    -------
    Dict[str, Any]
        {
            "summary": "...",
            "decisions": [...],
            "action_items": [...]
        }
    """

    stt_text = (stt_text or "").strip()
    ocr_text = (ocr_text or "").strip()
    title = (title or "").strip()

    prompt = _build_prompt(
        stt_text=stt_text,
        ocr_text=ocr_text,
        title=title,
    )

    return _call_llm(prompt)


def summarize_meeting_from_payload(payload: Dict[str, Any]) -> Dict[str, Any]:
    """
    STT JSON + OCR JSON payload 전체를 기반으로 회의 요약 수행

    주의
    ----
    - payload 안의 STT/OCR 데이터는 LLM 입력용으로 사용
    - 반환은 최종 summary 결과만 제공
    """

    if not payload:
        raise RuntimeError("LLM에 전달할 payload가 비어 있습니다.")

    prompt = _build_payload_prompt(payload)
    return _call_llm(prompt)


def summarize_meeting(
    stt_segments: Sequence[Any],
    ocr_text: str,
    title: str = "",
) -> Dict[str, Any]:
    """
    기존 호환용 함수:
    STT 세그먼트 배열 + OCR 텍스트를 기반으로 회의 요약 수행
    """

    stt_text = stt_json_to_text(stt_segments)

    return summarize_meeting_from_text(
        stt_text=stt_text,
        ocr_text=ocr_text,
        title=title,
    )