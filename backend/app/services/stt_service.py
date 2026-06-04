"""
stt_service.py

STT 전용 서비스 계층

역할
- 오디오 파일을 STT 서버에 전달
- STT 결과 텍스트 후처리
- STT 엔진/클라이언트와 나머지 서비스 계층을 분리

주의
- 파일 저장은 storage에서 담당
- transcript DB 저장은 audio_service 또는 repository에서 담당
- 이 서비스는 "STT 실행 자체"에 집중
"""

from __future__ import annotations

from ai.stt_client import request_stt
from utils.preprocess import normalize_transcript_text


def transcribe_audio_file(file_path: str) -> str:
    """
    오디오 파일 경로를 받아 STT를 수행하고 결과 텍스트를 반환

    Parameters
    ----------
    file_path : str
        전처리 또는 저장이 끝난 오디오 파일 경로

    Returns
    -------
    str
        STT 결과 텍스트
    """

    # 외부 STT 서버에 요청하여 텍스트 변환
    raw_text = request_stt(file_path)

    # transcript 후처리
    cleaned_text = normalize_transcript_text(raw_text)

    return cleaned_text