"""
routers 패키지 초기화 파일

역할
- FastAPI 라우터 객체들을 한 곳에서 export
- main.py에서 app.include_router(...) 할 때 사용
"""

from .auth_router import router as auth_router
from .meeting_router import router as meeting_router
from .upload_router import router as upload_router
from .decision_router import router as decision_router
from .action_item_router import router as action_item_router
from .folder_router import router as folder_router
from .file_router import router as file_router


__all__ = [
    "folder_router",
    "auth_router",
    "meeting_router",
    "upload_router",
    "decision_router",
    "action_item_router",
    "file_router",
]