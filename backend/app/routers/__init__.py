# Router 계층 패키지(placeholder).
# FastAPI 등 프레임워크 구현은 금지이며, 추후 API 경로만 분리해 둘 자리입니다.

from .auth_router import router as auth_router
from .meeting_router import router as meeting_router
from .upload_router import router as upload_router

__all__ = [
    "auth_router",
    "meeting_router",
    "upload_router",
]