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
            "assignee": "...",
            "due_date": "..."
        }
    ]
}

특징
- OpenAI 클라이언트는 config/openai_client.py를 사용
- 모델명은 config/settings.py에서 읽음
- STT와 OCR은 LLM 입력에는 함께 사용하지만,
  반환은 최종 요약 결과만 제공
- 긴 STT 입력이 들어와도 summary가 과하게 길어지지 않도록
  프롬프트에서 출력 길이와 중복 제거 규칙을 제한한다.
"""

from __future__ import annotations

import json
import logging
from typing import Any, Dict, Sequence

from config.openai_client import get_openai_client
from config.settings import settings
from utils.preprocess import stt_json_to_text

logger = logging.getLogger(__name__)


# 긴 회의록을 처리할 때 server.log가 너무 커지는 것을 막기 위한 로그 제한 길이
MAX_LOG_PROMPT_CHARS = 3000


def _build_common_rules() -> str:
    """
    text 기반 요약과 payload 기반 요약에서 공통으로 사용할 LLM 규칙.

    긴 음성을 5분 단위로 나눈 뒤 합친 transcript가 들어와도
    summary가 너무 길어지지 않도록 출력 길이 제한을 명확히 둔다.
    """

    return (
        "규칙:\n"
        '- 출력은 오직 유효한 JSON만 허용합니다(마크다운/설명/코드펜스 금지).\n'
        '- 입력에 명시적으로 없는 내용은 절대 추측하거나 추가하지 마세요.\n'
        '- 알 수 없는 값은 빈 문자열 "" 또는 빈 리스트 []로 두세요.\n'
        '- summary는 전체 회의 내용을 5~8문장 이내로 간결하게 작성하세요.\n'
        '- STT에 반복되는 말, 말버릇, 의미 없는 추임새는 제거하세요.\n'
        '- 원문을 길게 복사하지 말고 핵심 내용만 요약하세요.\n'
        '- decisions는 실제로 결정된 내용만 넣으세요.\n'
        '- action_items는 실제 할 일로 볼 수 있는 항목만 넣으세요.\n'
        '- action_items의 각 항목은 반드시 task, assignee, due_date 필드만 사용하세요.\n'
        '- 담당자나 기한이 명확하지 않으면 assignee 또는 due_date는 빈 문자열 ""로 두세요.\n'
    )


def _build_return_format() -> str:
    """
    LLM이 반드시 맞춰야 하는 반환 JSON 형식.
    """

    return (
        "반환 JSON 형식:\n"
        "{\n"
        '  "summary": "...",\n'
        '  "decisions": ["..."],\n'
        '  "action_items": [\n'
        '    { "task": "...", "assignee": "...", "due_date": "..." }\n'
        "  ]\n"
        "}\n"
    )


def _build_prompt(stt_text: str, ocr_text: str, title: str = "") -> str:
    """
    STT 텍스트 + OCR 텍스트를 LLM에 전달할 프롬프트 생성.
    """

    stt_text = (stt_text or "").strip()
    ocr_text = (ocr_text or "").strip()
    title = (title or "").strip()

    return (
        "당신은 회의 내용을 구조화하는 분석가입니다.\n"
        "다음 입력(STT 발언 + OCR 메모)을 근거로 회의 결과를 추출하세요.\n"
        "긴 STT는 여러 조각을 합친 결과일 수 있으므로, 전체 흐름을 보고 중복을 제거하세요.\n"
        "\n"
        f"{_build_common_rules()}"
        "\n"
        f"{_build_return_format()}"
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
    LLM에 전달할 프롬프트 생성.
    """

    payload_json = json.dumps(payload, ensure_ascii=False, indent=2)

    return (
        "당신은 회의 내용을 구조화하는 분석가입니다.\n"
        "입력으로 회의 메타데이터, STT JSON, OCR/이미지 분석 JSON이 주어집니다.\n"
        "이 정보를 종합하여 회의 결과를 추출하세요.\n"
        "긴 STT는 여러 조각을 합친 결과일 수 있으므로, 전체 흐름을 보고 중복을 제거하세요.\n"
        "\n"
        f"{_build_common_rules()}"
        '- STT 내용과 OCR/이미지 분석 내용을 함께 참고하세요.\n'
        '- OCR/이미지 분석 내용은 회의 맥락 보강용 자료이므로, STT와 충돌하면 더 보수적으로 요약하세요.\n'
        "\n"
        f"{_build_return_format()}"
        "\n"
        "입력 payload(JSON):\n"
        f"{payload_json}\n"
    )


def _extract_json_text(raw: str) -> str:
    """
    모델 응답에서 JSON 본문만 최대한 안전하게 추출.
    """

    raw = (raw or "").strip()

    if raw.startswith("{") and raw.endswith("}"):
        return raw

    start = raw.find("{")
    end = raw.rfind("}")

    if start != -1 and end != -1 and end > start:
        return raw[start:end + 1]

    return raw


def _normalize_summary_result(parsed: Dict[str, Any]) -> Dict[str, Any]:
    """
    LLM 결과를 앱에서 기대하는 형태로 보정한다.

    최종 구조
    --------
    {
        "summary": str,
        "decisions": list[str],
        "action_items": [
            {"task": str, "assignee": str, "due_date": str}
        ]
    }
    """

    summary = parsed.get("summary", "")
    if not isinstance(summary, str):
        summary = str(summary) if summary is not None else ""

    decisions = parsed.get("decisions", [])
    if not isinstance(decisions, list):
        decisions = []

    normalized_decisions: list[str] = []
    for item in decisions:
        if isinstance(item, str):
            value = item.strip()
            if value:
                normalized_decisions.append(value)

    action_items = parsed.get("action_items", [])
    if not isinstance(action_items, list):
        action_items = []

    normalized_action_items: list[dict[str, str]] = []
    for item in action_items:
        if not isinstance(item, dict):
            continue

        # 이전 프롬프트나 모델 출력과의 호환 처리
        task = item.get("task", "")
        assignee = item.get("assignee", item.get("owner", ""))
        due_date = item.get("due_date", item.get("deadline", ""))

        task = task.strip() if isinstance(task, str) else ""
        assignee = assignee.strip() if isinstance(assignee, str) else ""
        due_date = due_date.strip() if isinstance(due_date, str) else ""

        # task가 비어 있으면 할 일로 보기 어려우므로 제외
        if not task:
            continue

        normalized_action_items.append(
            {
                "task": task,
                "assignee": assignee,
                "due_date": due_date,
            }
        )

    return {
        "summary": summary.strip(),
        "decisions": normalized_decisions,
        "action_items": normalized_action_items,
    }


def _call_llm(prompt: str) -> Dict[str, Any]:
    """
    공통 OpenAI 호출 함수.

    Returns
    -------
    Dict[str, Any]
        구조화된 회의 요약 결과
    """

    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다.")

    client = get_openai_client()

    # 긴 STT 전체가 server.log에 그대로 쌓이는 것을 방지
    logger.info("===== LLM PROMPT START =====")
    logger.info("prompt length = %s", len(prompt))
    logger.info(prompt[:MAX_LOG_PROMPT_CHARS])
    if len(prompt) > MAX_LOG_PROMPT_CHARS:
        logger.info("... prompt truncated for log ...")
    logger.info("===== LLM PROMPT END =====")

    try:
        response = client.chat.completions.create(
            model=settings.openai_model,
            temperature=0.1,
            response_format={"type": "json_object"},
            messages=[
                {
                    "role": "system",
                    "content": (
                        "당신은 회의 요약 시스템입니다. "
                        "반드시 입력 내용에만 기반해서 JSON을 생성하세요. "
                        "입력에 없는 인물, 사건, 결정사항, 할 일을 절대 만들어내지 마세요. "
                        "반드시 유효한 JSON만 반환하세요. "
                        "반환 필드는 summary, decisions, action_items만 사용하세요."
                    ),
                },
                {
                    "role": "user",
                    "content": prompt,
                },
            ],
        )

        content = (response.choices[0].message.content or "").strip()

        logger.info("===== LLM RAW RESPONSE START =====")
        logger.info(content)
        logger.info("===== LLM RAW RESPONSE END =====")

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

    normalized = _normalize_summary_result(parsed)

    logger.info("===== FINAL PARSED JSON =====")
    logger.info(json.dumps(normalized, ensure_ascii=False, indent=2))

    return normalized


def summarize_meeting_from_text(
    stt_text: str,
    ocr_text: str,
    title: str = "",
) -> Dict[str, Any]:
    """
    STT 텍스트 + OCR 텍스트를 기반으로 회의 요약 수행.

    Returns
    -------
    Dict[str, Any]
        {
            "summary": "...",
            "decisions": [...],
            "action_items": [
                {"task": "...", "assignee": "...", "due_date": "..."}
            ]
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
    STT JSON + OCR JSON payload 전체를 기반으로 회의 요약 수행.

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
    STT 세그먼트 배열 + OCR 텍스트를 기반으로 회의 요약 수행.
    """

    stt_text = stt_json_to_text(stt_segments)

    return summarize_meeting_from_text(
        stt_text=stt_text,
        ocr_text=ocr_text,
        title=title,
    )
