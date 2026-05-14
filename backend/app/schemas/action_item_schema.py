"""
action_item_schema.py

할 일(ActionItem) 요청/응답 스키마
"""

from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class ActionItemCreate(BaseModel):
    task: str
    assignee: Optional[str] = None
    due_date: Optional[str] = None


class ActionItemUpdate(BaseModel):
    task: Optional[str] = None
    assignee: Optional[str] = None
    due_date: Optional[str] = None


class ActionItemResponse(BaseModel):
    id: int
    meeting_id: int
    task: str
    assignee: Optional[str] = None
    due_date: Optional[str] = None
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True