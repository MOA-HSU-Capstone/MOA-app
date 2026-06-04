"""
transcript_repository.py

Transcript 테이블에 대한 DB 접근 로직을 담당하는 Repository

역할
- transcript 생성
- 회의별 transcript 목록 조회
- transcript 삭제

가정
- 현재 프로젝트에서는 회의의 transcript를 누적 저장
- summary 생성 시 회의에 연결된 transcript 전체를 사용
"""

from __future__ import annotations

from sqlalchemy.orm import Session

from models.transcript_model import Transcript
from schemas.transcript_schema import TranscriptCreate


def create_transcript(db: Session, transcript_data: TranscriptCreate) -> Transcript:
    """
    transcript 생성

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    transcript_data : TranscriptCreate
        생성할 transcript 데이터
    """

    transcript = Transcript(
        meeting_id=transcript_data.meeting_id,
        content=transcript_data.content,
    )

    db.add(transcript)
    db.commit()
    db.refresh(transcript)

    return transcript


def get_transcripts_by_meeting_id(
    db: Session,
    meeting_id: int,
    skip: int = 0,
    limit: int = 100,
) -> list[Transcript]:
    """
    특정 회의의 transcript 목록 조회

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
    list[Transcript]
        해당 회의의 transcript 목록
    """

    return (
        db.query(Transcript)
        .filter(Transcript.meeting_id == meeting_id)
        .order_by(Transcript.created_at.desc())
        .offset(skip)
        .limit(limit)
        .all()
    )


def delete_transcript(db: Session, transcript: Transcript) -> None:
    """
    transcript 삭제

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    transcript : Transcript
        삭제할 Transcript ORM 객체
    """

    db.delete(transcript)
    db.commit()