import json
import logging
from pathlib import Path
from typing import Any, List

from app.services.meeting_service import process_meeting_text
from app.utils.preprocess import stt_json_to_text


def test_stt_json_basic() -> None:
    """일반 STT JSON 세그먼트 배열 → 번호 텍스트 변환 테스트."""
    segments: List[dict[str, Any]] = [
        {"start": 0.0, "end": 7.76, "text": "다음 주에도 화요일에 아시나요?"},
        {"start": 7.76, "end": 10.52, "text": "아니, 홍준은 저는 수요일 가능하시면"},
    ]

    text = stt_json_to_text(segments)
    assert text.startswith("1. "), "번호 리스트가 생성되어야 합니다."
    assert "\n2. " in text, "두 번째 줄 번호가 포함되어야 합니다."
    assert "화요일" in text and "수요일" in text, "세그먼트 text가 포함되어야 합니다."


def test_stt_json_dedup_noise() -> None:
    """내부 반복 축약 + 연속 동일 세그먼트 제거 테스트."""
    segments: List[dict[str, Any]] = [
        {"start": 10.52, "end": 12.08, "text": "수요일 가능해요, 수요일 가능해요"},
        {"start": 12.08, "end": 13.52, "text": "수요일 가능해요, 수요일 가능해요"},
        {"start": 129.36, "end": 130.36, "text": "서버"},
        {"start": 130.36, "end": 132.36, "text": "서버"},
        {"start": 132.36, "end": 133.36, "text": "서버"},
    ]

    text = stt_json_to_text(segments)
    assert text.count("수요일 가능해요") == 1, "내부 반복 축약이 적용되어야 합니다."
    assert text.count("서버") == 1, "연속 동일 세그먼트 제거가 적용되어야 합니다."
    assert text.startswith("1. "), "번호 리스트가 생성되어야 합니다."


def run_meeting_transcript_file_test() -> None:
    """
    meeting_transcript.json 파일을 읽어 전처리 + LLM 호출까지 수행하고,
    최종 결과를 JSON으로 출력합니다.
    """
    transcript_path = Path(r"C:\Users\SHINHYUNKYU\Documents\카카오톡 받은 파일\meeting_transcript.json")
    segments = json.loads(transcript_path.read_text(encoding="utf-8"))

    stt_text = stt_json_to_text(segments)
    logging.info("전처리된 STT 텍스트(앞 300자): %s", stt_text[:300])

    result = process_meeting_text(
        stt_raw=segments,
        ocr_text="(OCR 없음)",
        title="meeting_transcript.json 테스트",
    )

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)

    test_stt_json_basic()
    test_stt_json_dedup_noise()
    logging.info("STT JSON 변환 테스트 2건 통과")

    run_meeting_transcript_file_test()

