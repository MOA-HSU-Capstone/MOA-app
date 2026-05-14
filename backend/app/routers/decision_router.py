"""
decision_router.py

결정사항(Decision) API 라우터

제공 기능
- 회의에 결정사항 하나 추가
- 결정사항 하나 수정
- 결정사항 하나 삭제

주의
- 회의별 결정사항 목록 조회 API는 따로 만들지 않는다.
- 결정사항 목록은 회의 상세 조회 또는 요약 조회 응답에 포함해서 내려준다.
"""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from config.database import get_db
from schemas.decision_schema import (
    DecisionCreate,
    DecisionUpdate,
    DecisionResponse,
)
from services.decision_service import (
    create_meeting_decision,
    update_one_decision,
    delete_one_decision,
)


router = APIRouter(
    tags=["Decisions"],
)


@router.post(
    "/meetings/{meeting_id}/decisions",
    response_model=DecisionResponse,
)
def create_decision_api(
    meeting_id: int,
    request: DecisionCreate,
    db: Session = Depends(get_db),
):
    """
    회의에 결정사항 하나 추가

    요청 예시
    {
        "content": "회의 일정은 다음 주 월요일로 확정한다."
    }
    """

    return create_meeting_decision(
        db=db,
        meeting_id=meeting_id,
        request=request,
    )


@router.patch(
    "/decisions/{decision_id}",
    response_model=DecisionResponse,
)
def update_decision_api(
    decision_id: int,
    request: DecisionUpdate,
    db: Session = Depends(get_db),
):
    """
    결정사항 하나 수정

    요청 예시
    {
        "content": "수정된 결정사항 내용"
    }
    """

    return update_one_decision(
        db=db,
        decision_id=decision_id,
        request=request,
    )


@router.delete(
    "/decisions/{decision_id}",
)
def delete_decision_api(
    decision_id: int,
    db: Session = Depends(get_db),
):
    """
    결정사항 하나 삭제
    """

    return delete_one_decision(
        db=db,
        decision_id=decision_id,
    )