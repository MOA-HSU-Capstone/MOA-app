"""
meeting_schema.py

회의(Meeting) 관련 요청/응답 스키마 정의

역할
- 회의 생성 요청 검증
- 회의 수정 요청 검증
- 회의 응답 형식 정의
- FastAPI에서 request/response 모델로 사용

주의
- DB 테이블 구조는 models/meeting_model.py에서 관리
- 이 파일은 API 입력/출력 형식만 다룸
- DB에는 attendees를 문자열로 저장할 수 있지만,
  API에서는 list[str] 형태로 주고받는다.
- folder_id는 선택값이다.
"""

from __future__ import annotations

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class MeetingCreate(BaseModel):
    """
    회의 생성 요청 스키마

    사용 예
    -------
    POST /meetings

    {
        "title": "주간 회의",
        "folder_id": 1,
        "meeting_date": "2026.05.12",
        "meeting_time": "오후 2:00",
        "attendees": ["홍길동", "김철수"],
        "description": "DB 구조 및 API 설계 논의"
    }

    folder_id가 없거나 null이면 폴더 미지정 회의로 생성된다.
    """

    title: str = Field(
        ...,
        min_length=1,
        max_length=255,
        description="회의 제목",
    )

    folder_id: Optional[int] = Field(
        default=None,
        description="회의가 속한 폴더 ID. 없으면 폴더 미지정",
    )

    meeting_date: Optional[str] = Field(
        default=None,
        description="회의 날짜 예: 2026.05.12",
    )

    meeting_time: Optional[str] = Field(
        default=None,
        description="회의 시간 예: 오후 2:00",
    )

    attendees: list[str] = Field(
        default_factory=list,
        description="참석자 목록",
    )

    description: Optional[str] = Field(
        default=None,
        description="회의 설명",
    )


class MeetingUpdate(BaseModel):
    """
    회의 수정 요청 스키마

    부분 수정이 가능하도록 모든 필드를 optional로 둔다.

    사용 예
    -------
    PATCH /meetings/{meeting_id}

    {
        "title": "수정된 회의 제목",
        "folder_id": 1,
        "meeting_date": "2026.05.13",
        "meeting_time": "오후 3:00",
        "attendees": ["홍길동", "김철수", "이영희"],
        "description": "수정된 회의 설명"
    }

    폴더에서 회의를 빼고 싶으면:
    {
        "folder_id": null
    }
    """

    title: Optional[str] = Field(
        default=None,
        min_length=1,
        max_length=255,
        description="수정할 회의 제목",
    )

    folder_id: Optional[int] = Field(
        default=None,
        description="수정할 폴더 ID. null이면 폴더 미지정으로 변경",
    )

    meeting_date: Optional[str] = Field(
        default=None,
        description="수정할 회의 날짜",
    )

    meeting_time: Optional[str] = Field(
        default=None,
        description="수정할 회의 시간",
    )

    attendees: Optional[list[str]] = Field(
        default=None,
        description="수정할 참석자 목록",
    )

    description: Optional[str] = Field(
        default=None,
        description="수정할 회의 설명",
    )


class MeetingResponse(BaseModel):
    """
    회의 응답 스키마

    응답 예시
    --------
    {
        "id": 1,
        "folder_id": 1,
        "title": "주간 회의",
        "meeting_date": "2026.05.12",
        "meeting_time": "오후 2:00",
        "attendees": ["홍길동", "김철수"],
        "description": "DB 구조 및 API 설계 논의",
        "created_at": "2026-05-12T05:20:15",
        "updated_at": "2026-05-12T05:20:15"
    }
    """

    model_config = ConfigDict(from_attributes=True)

    id: int
    folder_id: Optional[int] = None
    title: str
    meeting_date: Optional[str] = None
    meeting_time: Optional[str] = None
    attendees: list[str] = Field(default_factory=list)
    description: Optional[str] = None
    created_at: datetime
    updated_at: datetime