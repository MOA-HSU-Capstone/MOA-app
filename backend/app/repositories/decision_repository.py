"""
decision_repository.py

결정사항(Decision) DB 접근 계층
"""

from sqlalchemy.orm import Session

from models.decision_model import Decision


def create_decision(
    db: Session,
    meeting_id: int,
    content: str,
) -> Decision:
    decision = Decision(
        meeting_id=meeting_id,
        content=content,
    )

    db.add(decision)
    db.commit()
    db.refresh(decision)

    return decision


def get_decision_by_id(
    db: Session,
    decision_id: int,
) -> Decision | None:
    return (
        db.query(Decision)
        .filter(Decision.id == decision_id)
        .first()
    )


def get_decisions_by_meeting_id(
    db: Session,
    meeting_id: int,
) -> list[Decision]:
    return (
        db.query(Decision)
        .filter(Decision.meeting_id == meeting_id)
        .order_by(Decision.id.asc())
        .all()
    )


def update_decision(
    db: Session,
    decision: Decision,
    content: str | None = None,
) -> Decision:
    if content is not None:
        decision.content = content

    db.commit()
    db.refresh(decision)

    return decision


def delete_decision(
    db: Session,
    decision: Decision,
) -> None:
    db.delete(decision)
    db.commit()


def delete_decisions_by_meeting_id(
    db: Session,
    meeting_id: int,
) -> None:
    (
        db.query(Decision)
        .filter(Decision.meeting_id == meeting_id)
        .delete()
    )

    db.commit()