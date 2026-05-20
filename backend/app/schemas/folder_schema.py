"""
folder_schema.py

폴더(Folder) 요청/응답 스키마

역할
- 폴더 생성 요청
- 폴더 수정 요청
- 폴더 응답 형식 정의
"""

from __future__ import annotations

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class FolderCreate(BaseModel):
    """
    폴더 생성 요청 스키마

    요청 예시
    {
        "name": "소프트웨어공학 회의"
    }
    """

    name: str = Field(..., min_length=1, max_length=100, description="폴더 이름")


class FolderUpdate(BaseModel):
    """
    폴더 수정 요청 스키마

    요청 예시
    {
        "name": "수정된 폴더명"
    }
    """

    name: Optional[str] = Field(
        default=None,
        min_length=1,
        max_length=100,
        description="수정할 폴더 이름",
    )


class FolderResponse(BaseModel):
    """
    폴더 응답 스키마
    """

    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    name: str
    created_at: datetime
    updated_at: datetime