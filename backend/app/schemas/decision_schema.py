"""
decision_schema.py

결정사항(Decision) 요청/응답 스키마
"""

from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class DecisionCreate(BaseModel):
    content: str


class DecisionUpdate(BaseModel):
    content: Optional[str] = None


class DecisionResponse(BaseModel):
    id: int
    meeting_id: int
    content: str
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True