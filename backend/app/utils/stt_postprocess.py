"""
utils/stt_postprocess.py

역할
- STT 결과 텍스트를 LLM 요약 전에 정리한다.
- 특정 분야나 기술 용어에 치우치지 않는 공통 후처리만 수행한다.
- 기술 용어, 프로젝트 용어, 서비스별 용어 보정은 domain_terms.py에서 처리한다.

주요 기능
- 공백/줄바꿈 정리
- 반복 표현 정리
- 불필요한 추임새 약하게 제거
- 문장부호 정리
- 너무 긴 문장 분리 보조
- 참석자 목록 기반 이름 보정
- 발음이 뭉개진 이름/회의 표현 보조
- 결정사항 후보 표시
- 할 일 후보 표시
- 기한 후보 표시
- 질문/답변 흐름 표시
- 역할 분담 후보 표시
"""

from __future__ import annotations

import re
from difflib import SequenceMatcher


GENERAL_REPLACEMENTS: dict[str, str] = {
    # 띄어쓰기 정리
    "확인 해": "확인해",
    "검토 해": "검토해",
    "수정 해": "수정해",
    "작성 해": "작성해",
    "정리 해": "정리해",
    "공유 해": "공유해",
    "준비 해": "준비해",
    "진행 해": "진행해",
    "추가 해": "추가해",
    "삭제 해": "삭제해",
    "제출 해": "제출해",
    "업로드 해": "업로드해",
    "전달 해": "전달해",
    "확정 해": "확정해",
    "결정 해": "결정해",

    # 요청/지시 표현 정리
    "확인해 주시고": "확인해주시고",
    "검토해 주시고": "검토해주시고",
    "수정해 주시고": "수정해주시고",
    "작성해 주시고": "작성해주시고",
    "정리해 주시고": "정리해주시고",
    "공유해 주시고": "공유해주시고",
    "준비해 주시고": "준비해주시고",
    "진행해 주시고": "진행해주시고",
    "담당해 주시고": "담당해주시고",
    "맡아 주시고": "맡아주시고",

    # 완곡 표현 정리
    "해야 될 것 같아요": "해야 할 것 같습니다",
    "하면 될 것 같아요": "하면 될 것 같습니다",
    "보면 될 것 같아요": "보면 될 것 같습니다",
    "정리하면 될 것 같아요": "정리하면 될 것 같습니다",
    "공유하면 될 것 같아요": "공유하면 될 것 같습니다",
    "준비하면 될 것 같아요": "준비하면 될 것 같습니다",
    "확인하면 될 것 같아요": "확인하면 될 것 같습니다",
    "검토하면 될 것 같아요": "검토하면 될 것 같습니다",

    # 흔한 표현 흔들림
    "할려고": "하려고",
    "볼려고": "보려고",
    "쓸려고": "쓰려고",
    "갈려고": "가려고",
    "할께요": "할게요",
    "볼께요": "볼게요",
    "갈께요": "갈게요",
}


FILLER_PATTERNS: list[str] = [
    r"(?<=\s)어(?=\s)",
    r"(?<=\s)음(?=\s)",
    r"(?<=\s)아(?=\s)",
    r"\b그니까\b",
    r"\b그러니까\b",
    r"\b뭐랄까\b",
    r"\b약간\b",
    r"\b일단\b",
    r"\b아무튼\b",
    r"\b어쨌든\b",
    r"\b뭔가\b",
]


ACTION_KEYWORDS: list[str] = [
    "확인",
    "검토",
    "수정",
    "작성",
    "정리",
    "공유",
    "준비",
    "진행",
    "추가",
    "삭제",
    "제출",
    "업로드",
    "전달",
    "테스트",
    "담당",
    "맡",
    "보내",
    "만들",
    "올리",
    "정하",
]


FUZZY_ACTION_WORDS: list[str] = [
    "확인",
    "검토",
    "수정",
    "작성",
    "정리",
    "공유",
    "준비",
    "진행",
    "추가",
    "삭제",
    "제출",
    "전달",
    "테스트",
    "담당",
]


DECISION_KEYWORDS: list[str] = [
    "결정",
    "확정",
    "정했습니다",
    "정했",
    "하기로 했",
    "하기로 하",
    "진행하기로",
    "준비하기로",
    "작성하기로",
    "정리하기로",
    "사용하기로",
    "변경하기로",
    "수정하기로",
    "채택",
]


FUZZY_DECISION_WORDS: list[str] = [
    "결정",
    "확정",
    "정했",
    "하기로",
    "진행하기로",
    "준비하기로",
    "작성하기로",
    "정리하기로",
    "변경하기로",
    "수정하기로",
]


DEADLINE_PATTERNS: list[str] = [
    r"오늘",
    r"내일",
    r"모레",
    r"이번\s*주",
    r"다음\s*주",
    r"이번\s*달",
    r"다음\s*달",
    r"\d{1,2}월\s*\d{1,2}일",
    r"\d{1,2}/\d{1,2}",
    r"\d{4}-\d{1,2}-\d{1,2}",
    r"오전\s*\d{1,2}시",
    r"오후\s*\d{1,2}시",
    r"\d{1,2}시",
    r"발표\s*전",
    r"회의\s*전",
    r"제출\s*전",
    r"마감",
    r"까지",
]


QUESTION_PATTERNS: list[str] = [
    r"질문",
    r"물어보",
    r"어떻게",
    r"왜",
    r"무엇",
    r"뭐가",
    r"어떤",
    r"가능한가",
    r"괜찮을까요",
]


def _replace_terms(text: str, replacements: dict[str, str]) -> str:
    """
    공통 표현을 치환한다.
    긴 표현부터 먼저 치환해서 부분 치환 충돌을 줄인다.
    """

    for wrong, correct in sorted(
        replacements.items(),
        key=lambda item: len(item[0]),
        reverse=True,
    ):
        text = text.replace(wrong, correct)

    return text


def _normalize_spaces(text: str) -> str:
    """
    공백과 줄바꿈을 정리한다.
    """

    text = text.replace("\t", " ")
    text = text.replace("\r\n", "\n")
    text = text.replace("\r", "\n")

    text = "\n".join(line.strip() for line in text.split("\n"))
    text = re.sub(r"[ ]{2,}", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)

    return text.strip()


def _compress_repeated_words(text: str) -> str:
    """
    STT에서 반복되는 짧은 표현을 줄인다.

    예:
    - 네 네 네 -> 네
    - 맞아요 맞아요 -> 맞아요
    """

    repeat_words = [
        "네",
        "예",
        "맞아요",
        "좋습니다",
        "알겠습니다",
        "음",
        "어",
        "아",
    ]

    for word in repeat_words:
        pattern = rf"({word})(\s+{word})+"
        text = re.sub(pattern, word, text)

    return _normalize_spaces(text)


def _remove_light_fillers(text: str) -> str:
    """
    말버릇/추임새를 약하게 제거한다.

    너무 강하게 제거하면 실제 발언 의미가 깨질 수 있으므로
    단독으로 등장하는 표현 위주로만 제거한다.
    """

    for pattern in FILLER_PATTERNS:
        text = re.sub(pattern, " ", text)

    return _normalize_spaces(text)


def _normalize_sentence_punctuation(text: str) -> str:
    """
    문장부호와 문장 경계를 약하게 정리한다.
    """

    text = text.replace(" .", ".")
    text = text.replace(" ,", ",")
    text = text.replace(" ?", "?")
    text = text.replace(" !", "!")

    text = re.sub(r"\.{2,}", ".", text)
    text = re.sub(r",{2,}", ",", text)
    text = re.sub(r"\?{2,}", "?", text)
    text = re.sub(r"!{2,}", "!", text)

    return _normalize_spaces(text)


def _split_overlong_sentences(text: str) -> str:
    """
    너무 긴 문장을 약하게 분리한다.

    STT는 긴 문장이 마침표 없이 이어지는 경우가 많아서
    LLM이 결정사항/할 일을 놓칠 수 있다.
    """

    split_keywords = [
        "그리고",
        "그러면",
        "다음으로",
        "또",
        "추가로",
        "마지막으로",
        "결론적으로",
        "정리하면",
    ]

    for keyword in split_keywords:
        text = text.replace(f" {keyword} ", f". {keyword} ")

    text = re.sub(r"\.{2,}", ".", text)

    return _normalize_spaces(text)


def _similarity(a: str, b: str) -> float:
    """
    문자열 유사도 계산.
    참석자 이름 보정과 발음 뭉개짐 후보 감지에 사용한다.
    """

    return SequenceMatcher(None, a, b).ratio()


def _contains_fuzzy_word(
    text: str,
    target_words: list[str],
    threshold: float = 0.67,
) -> bool:
    """
    텍스트 안에 target_words와 비슷한 단어가 있는지 확인한다.

    발음이 뭉개져 STT가 약간 다르게 인식한 경우를 잡기 위한 용도다.
    단, 실제 단어를 강제로 바꾸지는 않고 후보 마커 판단에만 사용한다.
    """

    words = re.findall(r"[가-힣A-Za-z0-9]+", text)

    for word in words:
        for target in target_words:
            if _similarity(word, target) >= threshold:
                return True

    return False


def _normalize_names_with_attendees(
    text: str,
    attendees: list[str] | None = None,
) -> str:
    """
    참석자 목록을 기준으로 이름 오인식을 제한적으로 보정한다.

    원칙
    - 참석자 목록이 없으면 이름 보정하지 않는다.
    - 참석자 목록에 없는 이름으로는 절대 보정하지 않는다.
    - 2~4글자 한글 이름 후보만 본다.
    - 유사도가 충분히 높을 때만 보정한다.

    예:
    - 참석자: ["민선", "형채", "민영"]
    - STT: "민산님" -> "민선님"
    - STT: "형체님" -> "형채님"
    """

    if not attendees:
        return text

    clean_attendees = [
        attendee.strip()
        for attendee in attendees
        if attendee and attendee.strip()
    ]

    if not clean_attendees:
        return text

    candidates = set(re.findall(r"[가-힣]{2,4}님?", text))

    for candidate in candidates:
        has_suffix = candidate.endswith("님")
        candidate_name = candidate.replace("님", "")

        best_name = ""
        best_score = 0.0

        for attendee in clean_attendees:
            score = _similarity(candidate_name, attendee)

            if score > best_score:
                best_score = score
                best_name = attendee

        if best_name and best_score >= 0.67:
            corrected = f"{best_name}님" if has_suffix else best_name
            text = text.replace(candidate, corrected)

    return text


def _mark_decision_candidates(text: str) -> str:
    """
    LLM이 결정사항을 더 잘 찾도록 표시를 추가한다.

    정확한 키워드뿐 아니라 발음이 뭉개진 유사 표현도 감지한다.
    """

    if "[결정 후보]" in text:
        return text

    exact_match = any(keyword in text for keyword in DECISION_KEYWORDS)
    fuzzy_match = _contains_fuzzy_word(text, FUZZY_DECISION_WORDS)

    if exact_match or fuzzy_match:
        return "[결정 후보]\n" + text

    return text


def _mark_action_candidates(text: str) -> str:
    """
    LLM이 할 일을 더 잘 찾도록 표시를 추가한다.

    정확한 키워드뿐 아니라 발음이 뭉개진 유사 표현도 감지한다.
    """

    if "[할 일 후보]" in text:
        return text

    exact_match = any(keyword in text for keyword in ACTION_KEYWORDS)
    fuzzy_match = _contains_fuzzy_word(text, FUZZY_ACTION_WORDS)

    if exact_match or fuzzy_match:
        return "[할 일 후보]\n" + text

    return text


def _mark_deadline_candidates(text: str) -> str:
    """
    LLM이 기한 표현을 더 잘 찾도록 표시를 추가한다.
    """

    if "[기한 후보]" in text:
        return text

    for pattern in DEADLINE_PATTERNS:
        if re.search(pattern, text):
            return "[기한 후보]\n" + text

    return text


def _mark_question_candidates(text: str) -> str:
    """
    회의에서 질문/답변 중심 흐름이 있을 때 표시를 추가한다.
    """

    if "[질문 후보]" in text:
        return text

    for pattern in QUESTION_PATTERNS:
        if re.search(pattern, text):
            return "[질문 후보]\n" + text

    return text


def _mark_role_assignment_candidates(text: str) -> str:
    """
    역할 분담 문장을 감지해서 LLM이 action_items로 잘 뽑도록 표시한다.

    특정 분야나 특정 이름에 의존하지 않고,
    일반적인 역할 분담 패턴만 감지한다.
    """

    if "[역할 분담 후보]" in text:
        return text

    patterns = [
        r"[가-힣A-Za-z0-9/ ]{1,30}[은는]\s*[가-힣]{2,4}님?이\s*[가-힣\s]*(준비|담당|정리|작성|검토|확인|수정|진행)",
        r"[가-힣A-Za-z0-9/ ]{1,30}[은는]\s*제가\s*[가-힣\s]*(준비|담당|정리|작성|검토|확인|수정|진행)",
        r"[가-힣]{2,4}님?이\s*[가-힣A-Za-z0-9/ ]{1,30}(을|를)\s*(준비|담당|정리|작성|검토|확인|수정|진행)",
    ]

    for pattern in patterns:
        if re.search(pattern, text):
            return "[역할 분담 후보]\n" + text

    return text


def _normalize_numbered_items(text: str) -> str:
    """
    번호가 붙은 항목을 조금 더 명확하게 만든다.

    예:
    - 1번은 -> 1번 항목은
    - 첫 번째는 -> 첫 번째 항목은
    """

    text = re.sub(r"(\d+)번은", r"\1번 항목은", text)
    text = re.sub(r"(\d+)번이", r"\1번 항목이", text)
    text = re.sub(r"(\d+)번을", r"\1번 항목을", text)

    text = text.replace("첫 번째는", "첫 번째 항목은")
    text = text.replace("두 번째는", "두 번째 항목은")
    text = text.replace("세 번째는", "세 번째 항목은")
    text = text.replace("네 번째는", "네 번째 항목은")
    text = text.replace("다섯 번째는", "다섯 번째 항목은")

    return _normalize_spaces(text)


def _remove_duplicate_marker_lines(text: str) -> str:
    """
    후처리 마커가 중복으로 붙는 것을 방지한다.
    """

    marker_order = [
        "[결정 후보]",
        "[할 일 후보]",
        "[기한 후보]",
        "[질문 후보]",
        "[역할 분담 후보]",
    ]

    lines = text.splitlines()
    seen_markers: set[str] = set()
    cleaned_lines: list[str] = []

    for line in lines:
        stripped = line.strip()

        if stripped in marker_order:
            if stripped in seen_markers:
                continue

            seen_markers.add(stripped)

        cleaned_lines.append(line)

    return "\n".join(cleaned_lines).strip()


def postprocess_stt_text(
    text: str,
    attendees: list[str] | None = None,
) -> str:
    """
    STT 결과 전체 후처리 함수.

    Parameters
    ----------
    text:
        STT transcript

    attendees:
        참석자 목록.
        이름 보정에만 제한적으로 사용한다.

    Returns
    -------
    str
        후처리된 transcript
    """

    if not text:
        return ""

    # 1. 기본 공백 정리
    text = _normalize_spaces(text)

    # 2. 반복 표현 축소
    text = _compress_repeated_words(text)

    # 3. 공통 표현 보정
    text = _replace_terms(text, GENERAL_REPLACEMENTS)

    # 4. 번호 항목 표현 정리
    text = _normalize_numbered_items(text)

    # 5. 참석자 목록 기반 이름 보정
    text = _normalize_names_with_attendees(text, attendees)

    # 6. 추임새 약하게 제거
    text = _remove_light_fillers(text)

    # 7. 문장부호 정리
    text = _normalize_sentence_punctuation(text)

    # 8. 너무 긴 문장 약하게 분리
    text = _split_overlong_sentences(text)

    # 9. 결정/할 일/기한/질문/역할 분담 후보 표시
    text = _mark_decision_candidates(text)
    text = _mark_action_candidates(text)
    text = _mark_deadline_candidates(text)
    text = _mark_question_candidates(text)
    text = _mark_role_assignment_candidates(text)

    # 10. 마커 중복 제거
    text = _remove_duplicate_marker_lines(text)

    # 11. 최종 공백 정리
    text = _normalize_spaces(text)

    return text