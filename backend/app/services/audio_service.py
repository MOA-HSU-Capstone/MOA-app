"""
audio_service.py

오디오 처리 서비스 계층

역할
- 업로드된 오디오 파일 저장
- 오디오 전처리
- STT 수행
- transcript DB 저장

흐름
upload_router
    ↓
audio_service
    ↓
file_manager
    ↓
preprocess
    ↓
stt_service
    ↓
transcript_repository
"""

from __future__ import annotations

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from repositories.meeting_repository import get_meeting_by_id
from repositories.transcript_repository import create_transcript
from schemas.transcript_schema import TranscriptCreate, TranscriptResponse
from services.stt_service import transcribe_audio_file
from storage.file_manager import save_audio_file
from utils.preprocess import preprocess_audio_file


def process_uploaded_audio(
    db: Session,
    meeting_id: int,
    upload_file,
) -> TranscriptResponse:
    """
    업로드된 오디오 파일을 저장하고 STT를 수행한 뒤 transcript를 DB에 저장

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    meeting_id : int
        이 오디오가 연결될 회의 ID

    upload_file : UploadFile
        FastAPI UploadFile 객체

    Returns
    -------
    TranscriptResponse
        저장된 transcript 응답 스키마

    동작 방식
    --------
    1. meeting_id에 해당하는 회의 존재 여부 확인
    2. 업로드된 오디오 파일을 meeting_id 전용 폴더에 저장
    3. 저장된 오디오 파일 전처리
    4. STT 수행
    5. transcript DB 저장
    6. 응답 스키마 반환
    """

    # 1. 회의 존재 여부 확인
    meeting = get_meeting_by_id(db, meeting_id)
    if meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 meeting_id의 회의를 찾을 수 없습니다.",
        )

    # 2. 오디오 파일 저장
    #    meeting_id를 함께 넘겨 회의별 폴더에 저장되도록 처리
    saved_path = save_audio_file(
        upload_file=upload_file,
        meeting_id=meeting_id,
    )

    # 3. 오디오 전처리
    processed_path = preprocess_audio_file(saved_path)

    # 4. STT 실행
    transcript_text = transcribe_audio_file(processed_path)

    # 5. transcript 생성 스키마 작성
    transcript_data = TranscriptCreate(
        meeting_id=meeting_id,
        content=transcript_text,
    )

    # 6. DB 저장
    transcript = create_transcript(db, transcript_data)

    # 7. 응답 스키마 변환
    return TranscriptResponse.model_validate(transcript)