"""
summary_schema.py

회의 요약(Summary) 관련 요청/응답 스키마 정의

현재 구조
- summaries 테이블: 회의 요약 본문 저장
- decisions 테이블: 결정사항 목록 저장
- action_items 테이블: 할 일 목록 저장
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from schemas.action_item_schema import ActionItemResponse
from schemas.decision_schema import DecisionResponse


class SummaryCreate(BaseModel):
    """
    summary 생성 요청 스키마

    DB의 summaries.content에 저장할 요약 본문
    """

    meeting_id: int = Field(..., gt=0, description="회의 ID")
    content: str = Field(default="", description="회의 요약 본문")

class ActionItemUpdate(BaseModel):
    """
    앱에서 수정한 action item 1건.
    """

    task: str = ""
    owner: str = ""
    deadline: str = ""


class SummaryUpdate(BaseModel):
    """
    앱 상세 화면에서 수정한 summary payload.

    DB에는 JSON 문자열로 저장하고, API 응답에서는 dict로 반환한다.
    """

    summary: str = ""
    decisions: list[str] = Field(default_factory=list)
    action_items: list[ActionItemUpdate] = Field(default_factory=list)
    error: str | None = None



class SummaryResponse(BaseModel):
    """
    summary DB 응답 스키마

    ORM 객체를 그대로 응답해야 할 때 사용
    """

    model_config = ConfigDict(from_attributes=True)

    id: int
    meeting_id: int
    content: str
    created_at: datetime
    updated_at: datetime


class SummaryGenerateResponse(BaseModel):
    """
    요약 생성 API 응답 스키마

    POST /meetings/{meeting_id}/summary 응답
    """

    meeting_id: int
    summary: dict[str, Any]


class SummaryUpdateRequest(BaseModel):
    """
    회의 summary 본문 수정 요청 스키마

    PATCH /meetings/{meeting_id}/summary

    주의
    ----
    결정사항과 할 일은 여기서 수정하지 않는다.
    각각 decision_router.py, action_item_router.py에서 개별 수정한다.
    """

    summary: str = Field(default="", description="수정할 회의 요약 본문")


class SummaryDetailResponse(BaseModel):
    """
    회의 요약 조회 API 응답 스키마

    summaries, decisions, action_items를 합쳐서 반환한다.
    """

    model_config = ConfigDict(from_attributes=True)

    id: int
    meeting_id: int
    summary: str
    decisions: list[DecisionResponse] = Field(default_factory=list)
    action_items: list[ActionItemResponse] = Field(default_factory=list)
    created_at: datetime
    updated_at: datetime