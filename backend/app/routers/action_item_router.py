"""
action_item_router.py

할 일(ActionItem) API 라우터

제공 기능
- 회의에 할 일 하나 추가
- 할 일 하나 수정
- 할 일 하나 삭제

주의
- 회의별 할 일 목록 조회 API는 따로 만들지 않는다.
- 할 일 목록은 회의 상세 조회 또는 요약 조회 응답에 포함해서 내려준다.
"""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from config.database import get_db
from schemas.action_item_schema import (
    ActionItemCreate,
    ActionItemUpdate,
    ActionItemResponse,
)
from services.action_item_service import (
    create_meeting_action_item,
    update_one_action_item,
    delete_one_action_item,
)


router = APIRouter(
    tags=["Action Items"],
)


@router.post(
    "/meetings/{meeting_id}/action-items",
    response_model=ActionItemResponse,
)
def create_action_item_api(
    meeting_id: int,
    request: ActionItemCreate,
    db: Session = Depends(get_db),
):
    """
    회의에 할 일 하나 추가

    요청 예시
    {
        "task": "보고서 작성",
        "assignee": "홍길동",
        "due_date": "2026.05.13"
    }
    """

    return create_meeting_action_item(
        db=db,
        meeting_id=meeting_id,
        request=request,
    )


@router.patch(
    "/action-items/{action_item_id}",
    response_model=ActionItemResponse,
)
def update_action_item_api(
    action_item_id: int,
    request: ActionItemUpdate,
    db: Session = Depends(get_db),
):
    """
    할 일 하나 수정

    요청 예시
    {
        "assignee": "김철수"
    }

    {
        "due_date": "2026.05.14"
    }

    {
        "task": "수정된 할 일"
    }
    """

    return update_one_action_item(
        db=db,
        action_item_id=action_item_id,
        request=request,
    )


@router.delete(
    "/action-items/{action_item_id}",
)
def delete_action_item_api(
    action_item_id: int,
    db: Session = Depends(get_db),
):
    """
    할 일 하나 삭제
    """

    return delete_one_action_item(
        db=db,
        action_item_id=action_item_id,
    )