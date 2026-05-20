"""
repositories 패키지

역할
- 각 Repository 모듈의 주요 함수들을 한 곳에서 export
- 서비스 계층에서 간단하게 import 가능하도록 구성

주의
- 실제 DB 연결은 config.database에서 처리
- 이 계층은 ORM 접근 로직만 담당
- 로그인 기능이 있으므로 사용자별 조회 함수 사용을 권장
"""
from .user_repository import (
    create_user,
    get_user_by_username,
)

from .folder_repository import (
    create_folder,
    get_folders_by_user_id,
    get_folder_by_id_and_user_id,
    update_folder,
    delete_folder,
)


from .image_repository import (
    create_image,
    delete_image,
    get_images_by_meeting_id,
)

from .meeting_repository import (
    create_meeting,
    get_meeting_by_id,
    get_meeting_by_id_and_user_id,
    get_meetings_by_user_id,
    get_meetings_by_user_id_and_folder_id,
    update_meeting,
    delete_meeting,
)

from .summary_repository import (
    create_summary,
    delete_summary,
    get_summary_by_meeting_id,
    update_summary,
    upsert_summary,
)

from .transcript_repository import (
    create_transcript,
    delete_transcript,
    get_transcripts_by_meeting_id,
)

from .decision_repository import (
    create_decision,
    get_decision_by_id,
    get_decisions_by_meeting_id,
    update_decision,
    delete_decision,
    delete_decisions_by_meeting_id,
)

from .action_item_repository import (
    create_action_item,
    get_action_item_by_id,
    get_action_items_by_meeting_id,
    update_action_item,
    delete_action_item,
    delete_action_items_by_meeting_id,
)

__all__ = [
     # user
    "create_user",
    "get_user_by_username",

    #folder
    "create_folder",
    "get_folders_by_user_id",
    "get_folder_by_id_and_user_id",
    "update_folder",
    "delete_folder",

    # meeting
    "create_meeting",
    "get_meeting_by_id",
    "get_meeting_by_id_and_user_id",
    "get_meetings_by_user_id",
    "get_meetings_by_user_id_and_folder_id",
    "update_meeting",
    "delete_meeting",

    # transcript
    "create_transcript",
    "get_transcripts_by_meeting_id",
    "delete_transcript",

    # summary
    "create_summary",
    "get_summary_by_meeting_id",
    "update_summary",
    "upsert_summary",
    "delete_summary",

    # image
    "create_image",
    "get_images_by_meeting_id",
    "delete_image",

    # decision_repository
    "create_decision",
    "get_decision_by_id",
    "get_decisions_by_meeting_id",
    "update_decision",
    "delete_decision",
    "delete_decisions_by_meeting_id",

    # action_item_repository
    "create_action_item",
    "get_action_item_by_id",
    "get_action_items_by_meeting_id",
    "update_action_item",
    "delete_action_item",
    "delete_action_items_by_meeting_id",
]