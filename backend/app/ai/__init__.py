"""
ai 패키지 초기화 파일

역할
- STT 요청 함수 export
- 이미지 OCR / 화이트보드 분석 함수 export
- 회의 요약 함수 export

포함 모듈
--------
image_ocr.py
- 이미지 OCR 처리
- image / whiteboard 타입별 처리

meeting_summarizer.py
- STT 텍스트와 OCR 텍스트를 기반으로 회의 요약 생성
- 여러 transcript를 합친 텍스트 기반 요약 생성

stt_client.py
- 외부 STT 서버 요청
"""

from .image_ocr import process_image_by_type
from .meeting_summarizer import summarize_meeting, summarize_meeting_from_text
from .stt_client import request_stt


__all__ = [
    # STT
    "request_stt",

    # Meeting summary
    "summarize_meeting",
    "summarize_meeting_from_text",

    # Image OCR
    "process_image_by_type",
]