"""
decision_service.py

결정사항(Decision) 서비스 계층

역할
- 회의에 결정사항 하나 추가
- 결정사항 하나 수정
- 결정사항 하나 삭제
"""

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from repositories.decision_repository import (
    create_decision,
    get_decision_by_id,
    update_decision,
    delete_decision,
)
from repositories.meeting_repository import get_meeting_by_id
from schemas.decision_schema import DecisionCreate, DecisionUpdate


def create_meeting_decision(
    db: Session,
    meeting_id: int,
    request: DecisionCreate,
):
    """
    회의에 결정사항 하나 추가
    """

    meeting = get_meeting_by_id(db, meeting_id)

    if meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="회의가 존재하지 않습니다.",
        )

    return create_decision(
        db=db,
        meeting_id=meeting_id,
        content=request.content,
    )


def update_one_decision(
    db: Session,
    decision_id: int,
    request: DecisionUpdate,
):
    """
    결정사항 하나 수정
    """

    decision = get_decision_by_id(
        db=db,
        decision_id=decision_id,
    )

    if decision is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="결정사항이 존재하지 않습니다.",
        )

    return update_decision(
        db=db,
        decision=decision,
        content=request.content,
    )


def delete_one_decision(
    db: Session,
    decision_id: int,
):
    """
    결정사항 하나 삭제
    """

    decision = get_decision_by_id(
        db=db,
        decision_id=decision_id,
    )

    if decision is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="결정사항이 존재하지 않습니다.",
        )

    delete_decision(
        db=db,
        decision=decision,
    )

    return {
        "message": "결정사항이 삭제되었습니다.",
        "decision_id": decision_id,
    }