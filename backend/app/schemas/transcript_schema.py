"""
transcript_schema.py

회의 음성의 STT 결과(Transcript) 관련 요청/응답 스키마 정의

역할
- transcript 응답 형식 정의
- transcript 생성/조회 시 API에서 사용

주의
- 실제 STT 처리 결과 저장은 models/transcript_model.py
- 이 파일은 API 응답 구조를 위한 스키마
"""

from __future__ import annotations

from datetime import datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class TranscriptCreate(BaseModel):
    """
    transcript 생성 요청 스키마

    주로 내부 로직 또는 테스트에서 사용할 수 있음
    실제 서비스에서는 STT 결과를 서비스 계층에서 저장하는 경우가 많음
    """

    meeting_id: int = Field(..., gt=0, description="회의 ID")
    content: str = Field(..., min_length=1, description="STT 결과 텍스트")

 
class TranscriptResponse(BaseModel):
    """
    transcript 응답 스키마
    """

    model_config = ConfigDict(from_attributes=True)

    id: int
    meeting_id: int
    content: str
    created_at: datetime
    updated_at: datetime


class TranscriptSimpleResponse(BaseModel):
    """
    transcript 단순 응답 스키마

    목록 조회나 간단한 응답에서 사용할 수 있는 경량 버전
    """

    model_config = ConfigDict(from_attributes=True)

    id: int
    meeting_id: int
    content: str