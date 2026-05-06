"""
image_repository.py

Image 테이블에 대한 DB 접근 로직을 담당하는 Repository

역할
- image 생성
- 회의별 image 목록 조회
- image 삭제

가정
- 현재 프로젝트에서는 회의에 여러 장의 image를 누적 저장
- summary 생성 시 회의에 연결된 image 전체를 사용할 수 있음
"""

from __future__ import annotations

from sqlalchemy.orm import Session

from models.image_model import Image
from schemas.image_schema import ImageCreate


def create_image(db: Session, image_data: ImageCreate) -> Image:
    """
    image 생성

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    image_data : ImageCreate
        생성할 image 데이터
    """

    image = Image(
        meeting_id=image_data.meeting_id,
        file_path=image_data.file_path,
        image_type=image_data.image_type,
        ocr_text=image_data.ocr_text,
        analysis_text=image_data.analysis_text,
    )

    db.add(image)
    db.commit()
    db.refresh(image)

    return image


def get_images_by_meeting_id(
    db: Session,
    meeting_id: int,
    skip: int = 0,
    limit: int = 100,
) -> list[Image]:
    """
    특정 회의의 image 목록 조회

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    meeting_id : int
        회의 ID

    skip : int
        건너뛸 개수

    limit : int
        최대 조회 개수

    Returns
    -------
    list[Image]
        해당 회의의 image 목록
    """

    return (
        db.query(Image)
        .filter(Image.meeting_id == meeting_id)
        .order_by(Image.created_at.desc())
        .offset(skip)
        .limit(limit)
        .all()
    )


def delete_image(db: Session, image: Image) -> None:
    """
    image 삭제

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    image : Image
        삭제할 Image ORM 객체
    """

    db.delete(image)
    db.commit()