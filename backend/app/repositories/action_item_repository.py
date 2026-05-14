"""
action_item_repository.py

할 일(ActionItem) DB 접근 계층
"""

from sqlalchemy.orm import Session

from models.action_item_model import ActionItem


def create_action_item(
    db: Session,
    meeting_id: int,
    task: str,
    assignee: str | None = None,
    due_date: str | None = None,
) -> ActionItem:
    action_item = ActionItem(
        meeting_id=meeting_id,
        task=task,
        assignee=assignee,
        due_date=due_date,
    )

    db.add(action_item)
    db.commit()
    db.refresh(action_item)

    return action_item


def get_action_item_by_id(
    db: Session,
    action_item_id: int,
) -> ActionItem | None:
    return (
        db.query(ActionItem)
        .filter(ActionItem.id == action_item_id)
        .first()
    )


def get_action_items_by_meeting_id(
    db: Session,
    meeting_id: int,
) -> list[ActionItem]:
    return (
        db.query(ActionItem)
        .filter(ActionItem.meeting_id == meeting_id)
        .order_by(ActionItem.id.asc())
        .all()
    )


def update_action_item(
    db: Session,
    action_item: ActionItem,
    task: str | None = None,
    assignee: str | None = None,
    due_date: str | None = None,
) -> ActionItem:
    if task is not None:
        action_item.task = task

    if assignee is not None:
        action_item.assignee = assignee

    if due_date is not None:
        action_item.due_date = due_date

    db.commit()
    db.refresh(action_item)

    return action_item


def delete_action_item(
    db: Session,
    action_item: ActionItem,
) -> None:
    db.delete(action_item)
    db.commit()


def delete_action_items_by_meeting_id(
    db: Session,
    meeting_id: int,
) -> None:
    (
        db.query(ActionItem)
        .filter(ActionItem.meeting_id == meeting_id)
        .delete()
    )

    db.commit()