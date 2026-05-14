"""
schemas 패키지 초기화 파일

역할
- 주요 스키마를 외부에서 쉽게 import 할 수 있도록 정리

예시
----
from schemas import MeetingCreate, MeetingResponse
"""

from .image_schema import ImageCreate, ImageResponse, ImageUploadResponse
from .meeting_schema import MeetingCreate, MeetingResponse, MeetingUpdate
from .summary_schema import (
    SummaryCreate,
    SummaryResponse,
    SummaryGenerateResponse,
    SummaryUpdateRequest,
    SummaryDetailResponse,
)
from .transcript_schema import (
    TranscriptCreate,
    TranscriptResponse,
    TranscriptSimpleResponse,
)
from .user_schema import (
    TokenResponse,
    UserCreate,
    UserLogin,
    UserResponse,
)
from .decision_schema import (
    DecisionCreate,
    DecisionUpdate,
    DecisionResponse,
)

from .action_item_schema import (
    ActionItemCreate,
    ActionItemUpdate,
    ActionItemResponse,
)


__all__ = [
    # meeting
    "MeetingCreate",
    "MeetingUpdate",
    "MeetingResponse",

    # transcript
    "TranscriptCreate",
    "TranscriptResponse",
    "TranscriptSimpleResponse",

    # summary
    "SummaryCreate",
    "SummaryResponse",
    "SummaryGenerateResponse",
    "SummaryUpdateRequest",
    "SummaryDetailResponse",

    # image
    "ImageCreate",
    "ImageResponse",
    "ImageUploadResponse",

    # decision_schema
    "DecisionCreate",
    "DecisionUpdate",
    "DecisionResponse",

    # action_item_schema
    "ActionItemCreate",
    "ActionItemUpdate",
    "ActionItemResponse",
    
    # user / auth
    "UserCreate",
    "UserLogin",
    "UserResponse",
    "TokenResponse",
]
