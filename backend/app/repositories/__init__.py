"""
repositories 패키지

역할
- 각 Repository 모듈의 주요 함수들을 한 곳에서 export
- 서비스 계층에서 간단하게 import 가능하도록 구성

주의
- 실제 DB 연결은 config.database에서 처리
- 이 계층은 ORM 접근 로직만 담당
"""

from .image_repository import (
    create_image,
    delete_image,
    get_images_by_meeting_id,
)
from .meeting_repository import (
    create_meeting,
    delete_meeting,
    get_all_meetings,
    get_meeting_by_id,
    update_meeting,
)
from .summary_repository import (
    create_summary,
    delete_summary,
    get_summary_by_meeting_id,
)
from .transcript_repository import (
    create_transcript,
    delete_transcript,
    get_transcripts_by_meeting_id,
)

__all__ = [
    # meeting
    "create_meeting",
    "get_meeting_by_id",
    "get_all_meetings",
    "update_meeting",
    "delete_meeting",

    # transcript
    "create_transcript",
    "get_transcripts_by_meeting_id",
    "delete_transcript",

    # summary
    "create_summary",
    "get_summary_by_meeting_id",
    "delete_summary",

    # image
    "create_image",
    "get_images_by_meeting_id",
    "delete_image",
]