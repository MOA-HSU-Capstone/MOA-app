"""
audio_service.py

오디오 처리 서비스 계층

역할
- 업로드된 오디오 파일 저장
- 오디오 전처리
- STT 서버 전송 전 wav 변환
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
audio_converter
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
from utils.audio_converter import convert_audio_to_wav


def process_uploaded_audio(
    db: Session,
    meeting_id: int,
    upload_file,
) -> TranscriptResponse:
    """
    업로드된 오디오 파일을 저장하고,
    STT 서버에 보내기 전에 wav 파일로 변환한 뒤,
    STT 결과를 transcript DB에 저장한다.

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
    4. STT 서버 전송용 wav 파일로 변환
    5. STT 수행
    6. transcript DB 저장
    7. 응답 스키마 반환
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
    #    기존 프로젝트의 preprocess_audio_file() 로직을 유지
    processed_path = preprocess_audio_file(saved_path)

    # 4. STT 서버로 보내기 전에 wav 파일로 변환
    #    Android에서 m4a, mp4, aac 등으로 녹음해도
    #    STT 서버에는 항상 wav 파일을 보내기 위한 단계
    try:
        wav_path = convert_audio_to_wav(processed_path)

    except FileNotFoundError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"오디오 파일을 찾을 수 없습니다: {str(e)}",
        )

    except RuntimeError as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"오디오 wav 변환 중 오류가 발생했습니다: {str(e)}",
        )

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"알 수 없는 오디오 변환 오류가 발생했습니다: {str(e)}",
        )

    # 5. STT 실행
    #    기존 processed_path가 아니라 wav_path를 넘겨야 함
    try:
        transcript_text = transcribe_audio_file(wav_path)

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"STT 처리 중 오류가 발생했습니다: {str(e)}",
        )

    # 6. transcript 생성 스키마 작성
    transcript_data = TranscriptCreate(
        meeting_id=meeting_id,
        content=transcript_text,
    )

    # 7. DB 저장
    transcript = create_transcript(db, transcript_data)

    # 8. 응답 스키마 변환
    return TranscriptResponse.model_validate(transcript)