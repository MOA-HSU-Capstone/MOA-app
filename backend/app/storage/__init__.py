"""
storage 패키지 초기화 파일

역할
- 파일 저장 및 업로드 경로 관련 함수들을 외부에서 쉽게 import 할 수 있도록 정리
"""

from .file_manager import save_audio_file, save_image_file
from .upload_paths import (
    BASE_UPLOAD_DIR,
    MEETINGS_UPLOAD_DIR,
    get_meeting_upload_dir,
    get_meeting_audio_dir,
    get_meeting_image_dir,
    ensure_base_upload_dirs,
    ensure_meeting_upload_dirs,
)


__all__ = [
    "BASE_UPLOAD_DIR",
    "MEETINGS_UPLOAD_DIR",
    "get_meeting_upload_dir",
    "get_meeting_audio_dir",
    "get_meeting_image_dir",
    "ensure_base_upload_dirs",
    "ensure_meeting_upload_dirs",
    "save_audio_file",
    "save_image_file",
]