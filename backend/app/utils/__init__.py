"""
utils 패키지 초기화 파일

역할
- 전처리, 오디오 변환, 보안, 인증 의존성 등 공통 함수를 제공
- 다른 계층에서 공통 유틸 함수를 쉽게 import할 수 있도록 정리
"""

from .preprocess import (
    collapse_internal_repetition,
    deduplicate_consecutive_segments,
    ensure_sentence_punctuation,
    normalize_text,
    normalize_transcript_text,
    preprocess_audio_file,
    preprocess_image_file,
    safe_json_dumps,
    stt_json_to_lines,
    stt_json_to_text,
)

from .audio_converter import (
    convert_audio_to_wav,
)

from .security import (
    create_access_token,
    hash_password,
    verify_password,
)

from .auth_dependency import (
    get_current_user,
)


__all__ = [
    # preprocess
    "preprocess_audio_file",
    "preprocess_image_file",
    "normalize_text",
    "collapse_internal_repetition",
    "ensure_sentence_punctuation",
    "deduplicate_consecutive_segments",
    "stt_json_to_lines",
    "stt_json_to_text",
    "normalize_transcript_text",
    "safe_json_dumps",

    # audio_converter
    "convert_audio_to_wav",

    # security
    "hash_password",
    "verify_password",
    "create_access_token",

    # auth dependency
    "get_current_user",
]