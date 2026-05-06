# Service 계층 패키지.
# 프레임워크와 무관하게 비즈니스 로직(오케스트레이션)을 제공하는 레이어입니다.
"""
services 패키지 초기화 파일

역할
- 서비스 계층의 주요 함수를 외부에서 쉽게 import 할 수 있도록 정리

예시
----
from services import create_new_meeting, process_uploaded_audio, register_user
"""

# audio
from .audio_service import process_uploaded_audio

# auth
from .auth_service import (
    login_user,
    register_user,
)

# image
from .image_service import (
    get_meeting_images,
    process_uploaded_image,
)

# meeting
from .meeting_service import (
    create_new_meeting,
    create_summary_for_meeting,
    get_full_transcript_for_meeting,
    get_meeting_detail,
    get_meeting_list,
    get_summary_for_meeting,
    remove_meeting,
    update_meeting_detail,
)

# stt
from .stt_service import transcribe_audio_file


__all__ = [
    # audio
    "process_uploaded_audio",

    # auth
    "register_user",
    "login_user",

    # image
    "process_uploaded_image",
    "get_meeting_images",

    # meeting
    "create_new_meeting",
    "get_meeting_detail",
    "get_meeting_list",
    "update_meeting_detail",
    "remove_meeting",
    "create_summary_for_meeting",
    "get_summary_for_meeting",
    "get_full_transcript_for_meeting",

    # stt
    "transcribe_audio_file",
]