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
    SummaryDetailResponse,
    SummaryGenerateResponse,
    SummaryResponse,
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
    "SummaryDetailResponse",

    # image
    "ImageCreate",
    "ImageResponse",
    "ImageUploadResponse",

    # user / auth
    "UserCreate",
    "UserLogin",
    "UserResponse",
    "TokenResponse",
]