"""
audio_service.py

오디오 처리 서비스 계층

역할
- 업로드된 오디오 파일 저장
- 오디오 전처리
- STT 서버 전송 전 wav 변환(단일) 또는 세그먼트 병합 후 wav
- STT 수행
- transcript DB 저장
- 여러 transcript를 하나의 combined_transcript로 합치기
- 최종 Summary 1개 생성 또는 갱신

현재 저장 구조
-------------
uploads/
└─ users/
    └─ {user_id}/
        └─ meetings/
            └─ {meeting_id}/
                ├─ audio/
                └─ images/
"""

from __future__ import annotations

from fastapi import HTTPException, UploadFile, status
from sqlalchemy.orm import Session

from ai.meeting_summarizer import summarize_meeting_from_text
from models.user_model import User
from repositories.meeting_repository import get_meeting_by_id_and_user_id
from repositories.summary_repository import upsert_summary
from repositories.transcript_repository import create_transcript
from schemas.summary_schema import SummaryCreate, SummaryResponse
from schemas.transcript_schema import TranscriptCreate, TranscriptResponse
from services.stt_service import transcribe_audio_file
from storage.file_manager import save_audio_file
from utils.audio_converter import convert_audio_to_wav, merge_audio_segments_to_wav
from utils.preprocess import normalize_transcript_text, preprocess_audio_file, safe_json_dumps


def _save_audio_files(
    upload_files: list[UploadFile],
    meeting_id: int,
    current_user: User,
) -> list[str]:
    saved_paths: list[str] = []

    for upload_file in upload_files:
        if not upload_file.filename:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="파일명이 비어 있는 오디오 파일이 포함되어 있습니다.",
            )

        saved_paths.append(
            save_audio_file(
                upload_file=upload_file,
                user_id=current_user.id,
                meeting_id=meeting_id,
            ),
        )

    return saved_paths


def _convert_or_merge_audio(processed_paths: list[str]) -> str:
    try:
        if len(processed_paths) == 1:
            return convert_audio_to_wav(processed_paths[0])
        return merge_audio_segments_to_wav(processed_paths)

    except FileNotFoundError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"오디오 파일을 찾을 수 없습니다: {str(e)}",
        ) from e

    except RuntimeError as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"오디오 wav 변환·병합 중 오류가 발생했습니다: {str(e)}",
        ) from e

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"알 수 없는 오디오 변환 오류가 발생했습니다: {str(e)}",
        ) from e


def _process_audio_files_to_transcript(
    db: Session,
    meeting_id: int,
    upload_files: list[UploadFile],
    current_user: User,
) -> TranscriptResponse | None:
    """
    업로드된 오디오(1개 이상)를 저장하고 STT 결과를 transcript DB에 저장한다.

    - 파일 1개: 단일 wav 변환 후 STT
    - 파일 2개 이상: 순서대로 저장 후 ffmpeg 병합 → 단일 wav → STT 1회
    """

    if not upload_files:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드된 오디오 파일이 없습니다.",
        )

    saved_paths = _save_audio_files(
        upload_files=upload_files,
        meeting_id=meeting_id,
        current_user=current_user,
    )

    try:
        processed_paths = [preprocess_audio_file(path) for path in saved_paths]
    except FileNotFoundError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"오디오 파일을 찾을 수 없습니다: {str(e)}",
        ) from e

    wav_path = _convert_or_merge_audio(processed_paths)

    try:
        transcript_text = transcribe_audio_file(wav_path)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"STT 처리 중 오류가 발생했습니다: {str(e)}",
        ) from e

    transcript_text = normalize_transcript_text(transcript_text)
    if not transcript_text:
        return None

    transcript_data = TranscriptCreate(
        meeting_id=meeting_id,
        content=transcript_text,
    )
    transcript = create_transcript(db, transcript_data)

    return TranscriptResponse.model_validate(transcript)


def _ensure_user_meeting(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> None:
    meeting = get_meeting_by_id_and_user_id(
        db=db,
        meeting_id=meeting_id,
        user_id=current_user.id,
    )

    if meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 회의를 찾을 수 없습니다.",
        )


def process_uploaded_audio(
    db: Session,
    meeting_id: int,
    upload_files: list[UploadFile],
    current_user: User,
) -> TranscriptResponse:
    """
    오디오 파일 1개 이상을 처리해 transcript를 저장한다.
    """

    _ensure_user_meeting(db=db, meeting_id=meeting_id, current_user=current_user)

    transcript_response = _process_audio_files_to_transcript(
        db=db,
        meeting_id=meeting_id,
        upload_files=upload_files,
        current_user=current_user,
    )

    if transcript_response is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="STT 결과가 비어 있어 transcript를 저장할 수 없습니다.",
        )

    return transcript_response


def process_uploaded_audio_files_and_create_summary(
    db: Session,
    meeting_id: int,
    upload_files: list[UploadFile],
    current_user: User,
) -> SummaryResponse:
    """
    여러 오디오 파일을 처리한 뒤 최종 Summary 1개를 생성 또는 갱신한다.
    """

    _ensure_user_meeting(db=db, meeting_id=meeting_id, current_user=current_user)

    transcript_response = _process_audio_files_to_transcript(
        db=db,
        meeting_id=meeting_id,
        upload_files=upload_files,
        current_user=current_user,
    )

    if transcript_response is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="STT 결과가 비어 있어 회의 요약을 생성할 수 없습니다.",
        )

    combined_transcript = normalize_transcript_text(transcript_response.content)
    if not combined_transcript:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="STT 결과가 비어 있어 회의 요약을 생성할 수 없습니다.",
        )

    try:
        summary_result = summarize_meeting_from_text(
            stt_text=combined_transcript,
            ocr_text="",
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"회의 요약 생성 중 오류가 발생했습니다: {str(e)}",
        ) from e

    summary_data = SummaryCreate(
        meeting_id=meeting_id,
        content=safe_json_dumps(summary_result),
    )
    meeting_summary = upsert_summary(db, summary_data)

    return SummaryResponse.model_validate(meeting_summary)


def process_uploaded_audio_files(
    db: Session,
    meeting_id: int,
    upload_files: list[UploadFile],
    current_user: User,
) -> list[TranscriptResponse]:
    """
    여러 오디오 파일을 각각 처리하고 transcript 목록만 반환한다.
    """

    _ensure_user_meeting(db=db, meeting_id=meeting_id, current_user=current_user)

    responses: list[TranscriptResponse] = []
    for upload_file in upload_files:
        transcript_response = _process_audio_files_to_transcript(
            db=db,
            meeting_id=meeting_id,
            upload_files=[upload_file],
            current_user=current_user,
        )
        if transcript_response is not None:
            responses.append(transcript_response)

    if not responses:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="모든 오디오 파일의 STT 결과가 비어 있어 transcript를 저장할 수 없습니다.",
        )

    return responses
