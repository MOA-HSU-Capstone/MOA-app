from __future__ import annotations

"""
preprocess.py

전처리 유틸리티 모듈

역할
----
1. 업로드된 오디오/이미지 파일의 기본 유효성 확인
2. STT 결과 JSON 또는 문자열을 사람이 읽기 좋은 텍스트로 정리
3. 요약 전에 사용할 텍스트를 정규화

전처리 정책
----------
- 이 파일은 오디오/이미지 파일의 "존재 여부 확인"과
  STT/OCR 텍스트 정리 유틸리티를 담당한다.
- 오디오 파일의 .wav 변환은 utils/audio_converter.py에서 담당한다.
- 이 파일에서는 의미를 새로 해석/요약/재작성하지 않는다.
- 허용 작업:
  - 노이즈성 공백 제거
  - 연속 공백 정리
  - 완전 동일 반복 축약
  - 연속 동일 세그먼트 제거
  - 번호 붙이기
  - 문장부호 보정

주의
----
- preprocess_audio_file()은 현재 파일 존재 여부만 확인하고
  입력받은 파일 경로를 그대로 반환한다.
- mp3, m4a, mp4, aac 등의 .wav 변환은
  utils/audio_converter.py의 convert_audio_to_wav()에서 처리한다.
"""

import json
import os
import re
from typing import Any, Dict, List, Sequence


def preprocess_audio_file(file_path: str) -> str:
    """
    오디오 파일 전처리 함수.

    현재 버전에서 하는 일
    -------------------
    - 파일 존재 여부 확인
    - 필요 시 추후 확장을 위한 전처리 진입점 유지
    - 현재는 원본 경로를 그대로 반환

    추후 확장 가능 작업
    -------------------
    - 무음 구간 제거
    - 잡음 제거
    - 볼륨 정규화

    참고
    ----
    - mp3, m4a, mp4, aac 등의 .wav 변환은
      utils/audio_converter.py의 convert_audio_to_wav()에서 처리한다.

    Parameters
    ----------
    file_path : str
        저장된 오디오 파일 경로

    Returns
    -------
    str
        전처리 후 사용할 오디오 파일 경로
    """

    if not os.path.exists(file_path):
        raise FileNotFoundError(f"오디오 파일을 찾을 수 없습니다: {file_path}")

    return file_path


def preprocess_image_file(file_path: str) -> str:
    """
    이미지 파일 전처리 함수.

    현재 버전에서 하는 일
    -------------------
    - 파일 존재 여부 확인
    - 필요 시 추후 확장을 위한 전처리 진입점 유지
    - 현재는 원본 경로를 그대로 반환

    추후 확장 가능 작업
    -------------------
    - 이미지 리사이즈
    - 회전 보정
    - 흑백 변환
    - 대비 향상

    Parameters
    ----------
    file_path : str
        저장된 이미지 파일 경로

    Returns
    -------
    str
        전처리 후 사용할 이미지 파일 경로
    """

    if not os.path.exists(file_path):
        raise FileNotFoundError(f"이미지 파일을 찾을 수 없습니다: {file_path}")

    return file_path


def normalize_text(text: str) -> str:
    """
    텍스트의 기본 공백을 정리한다.

    동작
    ----
    - None 또는 빈 문자열 방어
    - CRLF, CR 개행을 LF로 통일
    - 탭을 공백으로 변경
    - 개행 주변 공백 제거 후 한 줄로 평탄화
    - 연속 공백을 하나로 축소
    - 앞뒤 공백 제거

    Parameters
    ----------
    text : str
        정리할 원본 텍스트

    Returns
    -------
    str
        공백이 정리된 텍스트
    """

    text = (text or "").replace("\r\n", "\n").replace("\r", "\n")
    text = text.replace("\t", " ")

    # STT 세그먼트 단위로 줄바꿈을 다시 만들기 때문에
    # 세그먼트 내부 개행은 공백으로 평탄화한다.
    text = re.sub(r"\s*\n\s*", " ", text)

    # 연속 공백 제거
    text = re.sub(r"[ ]{2,}", " ", text).strip()

    return text


def collapse_internal_repetition(text: str) -> str:
    """
    한 세그먼트 내부에서 완전히 동일한 구절이 반복되면 1회만 남긴다.

    예
    --
    - "수요일 가능해요, 수요일 가능해요" -> "수요일 가능해요"
    - "서버 서버 서버" -> "서버"

    주의
    ----
    - 의미가 비슷한 문장을 임의로 합치지는 않는다.
    - 완전히 동일한 반복만 제거한다.

    Parameters
    ----------
    text : str
        STT 세그먼트 텍스트

    Returns
    -------
    str
        반복이 축약된 텍스트
    """

    text = normalize_text(text)
    if not text:
        return ""

    # 1. 쉼표로 나뉜 구절이 전부 동일하면 1회만 유지
    comma_parts = [part.strip() for part in text.split(",") if part.strip()]
    if len(comma_parts) >= 2 and all(part == comma_parts[0] for part in comma_parts):
        return comma_parts[0]

    # 2. 공백 토큰이 전부 동일하면 1회만 유지
    tokens = [token for token in text.split(" ") if token]
    if len(tokens) >= 2 and all(token == tokens[0] for token in tokens):
        return tokens[0]

    return text


def ensure_sentence_punctuation(text: str) -> str:
    """
    줄 끝에 문장부호가 없으면 마침표를 추가한다.

    Parameters
    ----------
    text : str
        문장부호를 보정할 텍스트

    Returns
    -------
    str
        문장부호가 보정된 텍스트
    """

    text = (text or "").strip()

    if not text:
        return ""

    # 이미 문장부호로 끝나면 그대로 반환
    if re.search(r"[.!?。！？…]$", text):
        return text

    return f"{text}."


def deduplicate_consecutive_segments(lines: List[str]) -> List[str]:
    """
    연속으로 동일하게 반복되는 STT 세그먼트를 제거한다.

    예
    --
    ["회의 시작합니다.", "회의 시작합니다.", "안건은 DB입니다."]
    -> ["회의 시작합니다.", "안건은 DB입니다."]

    Parameters
    ----------
    lines : List[str]
        정리된 STT 세그먼트 목록

    Returns
    -------
    List[str]
        연속 중복이 제거된 세그먼트 목록
    """

    result: List[str] = []
    previous_line: str | None = None

    for line in lines:
        if not line:
            continue

        if previous_line is not None and line == previous_line:
            continue

        result.append(line)
        previous_line = line

    return result


def _extract_text_from_segment(segment: Dict[str, Any]) -> str:
    """
    STT 세그먼트 dict에서 text 값을 안전하게 추출한다.

    Parameters
    ----------
    segment : Dict[str, Any]
        Whisper 스타일 STT 세그먼트

    Returns
    -------
    str
        추출된 text 문자열
    """

    value = segment.get("text")

    if not isinstance(value, str):
        return ""

    return value


def stt_json_to_lines(segments: Sequence[Any]) -> List[str]:
    """
    Whisper 스타일 STT 세그먼트 배열을 줄 목록으로 변환한다.

    방어 처리
    --------
    - dict가 아니면 무시
    - text 키가 없으면 무시
    - text가 문자열이 아니면 무시
    - 정리 결과가 빈 문자열이면 제거

    Parameters
    ----------
    segments : Sequence[Any]
        Whisper 스타일 STT 세그먼트 배열

    Returns
    -------
    List[str]
        사람이 읽기 좋게 정리된 STT 줄 목록
    """

    lines: List[str] = []

    for item in segments:
        if not isinstance(item, dict):
            continue

        raw_text = _extract_text_from_segment(item)

        text = collapse_internal_repetition(raw_text)
        text = normalize_text(text)

        if not text:
            continue

        text = ensure_sentence_punctuation(text)
        lines.append(text)

    return deduplicate_consecutive_segments(lines)


def stt_json_to_text(segments: Sequence[Any]) -> str:
    """
    STT 세그먼트 배열을 번호가 붙은 멀티라인 문자열로 변환한다.

    예
    --
    [
        {"text": "회의 시작합니다"},
        {"text": "오늘 안건은 DB입니다"}
    ]

    결과
    ----
    1. 회의 시작합니다.
    2. 오늘 안건은 DB입니다.

    Parameters
    ----------
    segments : Sequence[Any]
        Whisper 스타일 STT 세그먼트 배열

    Returns
    -------
    str
        번호가 붙은 STT 텍스트
    """

    lines = stt_json_to_lines(segments)

    if not lines:
        return ""

    return "\n".join(f"{index}. {line}" for index, line in enumerate(lines, start=1))


def normalize_transcript_text(text: str) -> str:
    """
    이미 문자열로 만들어진 transcript를 정리한다.

    역할
    ----
    - 앞뒤 공백 제거
    - 빈 줄 제거
    - 연속 공백 축소

    사용 예
    -------
    - STT 서버가 문자열을 직접 반환했을 때
    - 저장 전 transcript를 정리할 때

    Parameters
    ----------
    text : str
        STT 서버에서 받은 transcript 문자열

    Returns
    -------
    str
        정리된 transcript 문자열
    """

    if not text:
        return ""

    lines = [line.strip() for line in text.splitlines()]
    lines = [line for line in lines if line]

    normalized = "\n".join(lines)
    normalized = re.sub(r"[ \t]+", " ", normalized)

    return normalized.strip()


def safe_json_dumps(data: Any) -> str:
    """
    dict/list 등의 데이터를 JSON 문자열로 안전하게 변환한다.

    Parameters
    ----------
    data : Any
        JSON 직렬화할 데이터

    Returns
    -------
    str
        JSON 문자열
    """

    return json.dumps(data, ensure_ascii=False)