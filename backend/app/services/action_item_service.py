"""
action_item_service.py

할 일(ActionItem) 서비스 계층

역할
- 회의에 할 일 하나 추가
- 할 일 하나 수정
- 할 일 하나 삭제

제외한 기능
- 완료 체크 is_done
- 담당자별 할 일 조회
- 회의별 할 일 목록 조회 API
"""

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from repositories.action_item_repository import (
    create_action_item,
    get_action_item_by_id,
    update_action_item,
    delete_action_item,
)
from repositories.meeting_repository import get_meeting_by_id
from schemas.action_item_schema import ActionItemCreate, ActionItemUpdate


def create_meeting_action_item(
    db: Session,
    meeting_id: int,
    request: ActionItemCreate,
):
    """
    회의에 할 일 하나 추가
    """

    meeting = get_meeting_by_id(db, meeting_id)

    if meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="회의가 존재하지 않습니다.",
        )

    return create_action_item(
        db=db,
        meeting_id=meeting_id,
        task=request.task,
        assignee=request.assignee,
        due_date=request.due_date,
    )


def update_one_action_item(
    db: Session,
    action_item_id: int,
    request: ActionItemUpdate,
):
    """
    할 일 하나 수정

    task, assignee, due_date 중 일부만 수정 가능
    """

    action_item = get_action_item_by_id(
        db=db,
        action_item_id=action_item_id,
    )

    if action_item is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="할 일이 존재하지 않습니다.",
        )

    return update_action_item(
        db=db,
        action_item=action_item,
        task=request.task,
        assignee=request.assignee,
        due_date=request.due_date,
    )


def delete_one_action_item(
    db: Session,
    action_item_id: int,
):
    """
    할 일 하나 삭제
    """

    action_item = get_action_item_by_id(
        db=db,
        action_item_id=action_item_id,
    )

    if action_item is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="할 일이 존재하지 않습니다.",
        )

    delete_action_item(
        db=db,
        action_item=action_item,
    )

    return {
        "message": "할 일이 삭제되었습니다.",
        "action_item_id": action_item_id,
    }