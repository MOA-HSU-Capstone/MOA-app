"""
services 패키지 초기화 파일

역할
- 서비스 계층의 주요 함수를 외부에서 쉽게 import 할 수 있도록 정리
- 프레임워크와 무관한 비즈니스 로직/오케스트레이션 계층을 export

예시
----
from services import create_new_meeting, process_uploaded_audio, register_user
from services import process_uploaded_audio_files_and_create_summary
"""

# audio
from .audio_service import (
    process_uploaded_audio,
    process_uploaded_audio_files,
    process_uploaded_audio_files_and_create_summary,
)

# auth
from .auth_service import (
    login_user,
    register_user,
)

# image
from .image_service import (
    get_meeting_images,
    process_uploaded_image,
    process_uploaded_image_files,
)

# meeting
from .meeting_service import (
    create_new_meeting,
    get_meeting_detail,
    get_meeting_list,
    update_meeting_detail,
    remove_meeting,
    get_full_transcript_for_meeting,
    attendees_list_to_text,
    attendees_text_to_list,
    meeting_to_response,
)
#summary
from .summary_service import (
    create_summary_for_meeting,
    get_summary_for_meeting,
    update_summary_for_meeting,
)

#decision
from .decision_service import (
    create_meeting_decision,
    update_one_decision,
    delete_one_decision,
)

#action_item
from .action_item_service import (
    create_meeting_action_item,
    update_one_action_item,
    delete_one_action_item,
)

# stt
from .stt_service import transcribe_audio_file


__all__ = [
    # audio
    "process_uploaded_audio",
    "process_uploaded_audio_files",
    "process_uploaded_audio_files_and_create_summary",

    # auth
    "register_user",
    "login_user",

    # image
    "process_uploaded_image",
    "process_uploaded_image_files",
    "get_meeting_images",

    # meeting_service
    "create_new_meeting",
    "get_meeting_detail",
    "get_meeting_list",
    "update_meeting_detail",
    "remove_meeting",
    "get_full_transcript_for_meeting",
    "attendees_list_to_text",
    "attendees_text_to_list",
    "meeting_to_response",

    # summary_service
    "create_summary_for_meeting",
    "get_summary_for_meeting",
    "update_summary_for_meeting",

    # decision_service
    "create_meeting_decision",
    "update_one_decision",
    "delete_one_decision",

    # action_item_service
    "create_meeting_action_item",
    "update_one_action_item",
    "delete_one_action_item",

    # stt
    "transcribe_audio_file",
]