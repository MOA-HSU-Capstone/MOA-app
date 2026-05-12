"""
storage 패키지 초기화 파일

역할
- 파일 저장 및 업로드 경로 관련 함수들을 외부에서 쉽게 import 할 수 있도록 정리
- user_id + meeting_id 기반 저장 구조를 사용

저장 구조
---------
uploads/
└─ users/
    └─ {user_id}/
        └─ meetings/
            └─ {meeting_id}/
                ├─ audio/
                └─ images/
"""

from .file_manager import save_audio_file, save_image_file
from .upload_paths import (
    BASE_UPLOAD_DIR,
    USERS_UPLOAD_DIR,
    get_user_upload_dir,
    get_user_meetings_dir,
    get_user_meeting_upload_dir,
    get_user_meeting_audio_dir,
    get_user_meeting_image_dir,
    ensure_base_upload_dirs,
    ensure_user_meeting_upload_dirs,
)


__all__ = [
    # base dirs
    "BASE_UPLOAD_DIR",
    "USERS_UPLOAD_DIR",

    # path helpers
    "get_user_upload_dir",
    "get_user_meetings_dir",
    "get_user_meeting_upload_dir",
    "get_user_meeting_audio_dir",
    "get_user_meeting_image_dir",

    # directory creation
    "ensure_base_upload_dirs",
    "ensure_user_meeting_upload_dirs",

    # file save
    "save_audio_file",
    "save_image_file",
]