"""
audio_service.py

오디오 처리 서비스 계층

역할
- 업로드된 오디오 파일 저장
- 오디오 전처리
- STT 서버 전송 전 wav 변환
- STT 수행
- transcript DB 저장
- 여러 오디오 파일 처리
- 여러 transcript를 하나의 combined_transcript로 합치기
- combined_transcript를 LLM 요약 함수에 한 번만 전달
- 최종 Summary 1개 생성 또는 갱신

흐름
upload_router
    ↓
audio_service
    ↓
meeting_repository
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
    ↓
meeting_summarizer
    ↓
summary_repository

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

from models.user_model import User
from repositories.meeting_repository import get_meeting_by_id_and_user_id
from repositories.transcript_repository import create_transcript
from repositories.summary_repository import upsert_summary

from schemas.transcript_schema import TranscriptCreate, TranscriptResponse
from schemas.summary_schema import SummaryCreate, SummaryResponse

from services.stt_service import transcribe_audio_file
from storage.file_manager import save_audio_file
from utils.audio_converter import convert_audio_to_wav
from utils.audio_splitter import split_wav_file
from utils.preprocess import (
    preprocess_audio_file,
    normalize_transcript_text,
    safe_json_dumps,
)

# combined_transcript는 이미 문자열이므로
# summarize_meeting()이 아니라 summarize_meeting_from_text()를 사용한다.
from ai.meeting_summarizer import summarize_meeting_from_text


def _process_single_audio_to_transcript(
    db: Session,
    meeting_id: int,
    upload_file: UploadFile,
    current_user: User,
) -> TranscriptResponse | None:
    """
    오디오 파일 1개를 처리해서 transcript DB에 저장한다.

    동작 방식
    --------
    1. 파일명 확인
    2. 현재 로그인한 사용자 기준 폴더에 오디오 파일 저장
    3. 오디오 파일 기본 전처리
    4. wav 변환
    5. wav 파일을 5분 단위로 분할한 뒤 STT 수행
    6. STT 결과 텍스트 정규화
    7. STT 결과가 비어 있으면 저장하지 않고 None 반환
    8. transcript DB 저장
    9. TranscriptResponse 반환
    """

    # 1. 파일명 확인
    if not upload_file.filename:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="파일명이 비어 있는 오디오 파일이 포함되어 있습니다.",
        )

    # 2. 오디오 파일 저장
    saved_path = save_audio_file(
        upload_file=upload_file,
        user_id=current_user.id,
        meeting_id=meeting_id,
    )

    # 3. 오디오 파일 기본 전처리
    try:
        processed_path = preprocess_audio_file(saved_path)

    except FileNotFoundError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"오디오 파일을 찾을 수 없습니다: {str(e)}",
        )

    # 4. STT 서버 전송 전 wav 변환
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

    # 5. 긴 wav 파일을 5분 단위로 분할한 뒤 STT 실행
    try:
        split_paths = split_wav_file(
            wav_path=wav_path,
            segment_seconds=300,
        )

        transcript_parts: list[str] = []

        for index, split_path in enumerate(split_paths, start=1):
            print(
                f"STT split file {index}/{len(split_paths)} = {split_path}",
                flush=True,
            )

            part_text = transcribe_audio_file(split_path)
            part_text = normalize_transcript_text(part_text)

            if part_text:
                transcript_parts.append(part_text)

        transcript_text = "\n\n".join(transcript_parts)

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"STT 처리 중 오류가 발생했습니다: {str(e)}",
        )

    # 6. STT 결과 텍스트 정규화
    transcript_text = normalize_transcript_text(transcript_text)

    # 7. STT 결과가 비어 있으면 DB 저장하지 않음
    if not transcript_text:
        return None

    # 8. transcript 생성 스키마 작성
    transcript_data = TranscriptCreate(
        meeting_id=meeting_id,
        content=transcript_text,
    )

    # 9. transcript DB 저장
    transcript = create_transcript(db, transcript_data)

    # 10. 응답 스키마 변환
    return TranscriptResponse.model_validate(transcript)


def process_uploaded_audio(
    db: Session,
    meeting_id: int,
    upload_file: UploadFile,
    current_user: User,
) -> TranscriptResponse:
    """
    오디오 파일 1개 업로드 처리 함수.

    기존 단일 파일 업로드 API가 필요할 수 있으므로 유지한다.

    동작 방식
    --------
    1. 현재 로그인한 사용자의 회의인지 확인
    2. 오디오 파일 1개 처리
    3. transcript DB 저장
    4. TranscriptResponse 반환
    """

    # 1. 현재 로그인한 사용자의 회의인지 확인
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

    # 2. 오디오 파일 1개 처리
    transcript_response = _process_single_audio_to_transcript(
        db=db,
        meeting_id=meeting_id,
        upload_file=upload_file,
        current_user=current_user,
    )

    # 3. STT 결과가 비어 있으면 transcript_response가 None
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

    요구사항
    --------
    - files: list[UploadFile] = File(...) 로 여러 오디오 파일 받기
    - 현재 로그인한 사용자의 회의인지 확인
    - 각 파일 저장
    - 각 파일에 대해 STT 수행
    - 파일별 transcript 생성
    - 모든 transcript를 하나의 combined_transcript로 합치기
    - combined_transcript를 LLM 요약 함수에 한 번만 전달
    - 최종 summary는 meeting_id 기준으로 1개만 유지

    Returns
    -------
    SummaryResponse
        최종 회의 요약 응답
    """

    # 1. 파일 목록 확인
    if not upload_files:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드된 오디오 파일이 없습니다.",
        )

    # 2. 현재 로그인한 사용자의 회의인지 확인
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

    # 3. 파일별 transcript 응답 저장 리스트
    transcript_responses: list[TranscriptResponse] = []

    # 4. 여러 오디오 파일을 하나씩 처리
    for upload_file in upload_files:
        transcript_response = _process_single_audio_to_transcript(
            db=db,
            meeting_id=meeting_id,
            upload_file=upload_file,
            current_user=current_user,
        )

        # STT 결과가 빈 파일은 None이 반환되므로 건너뛴다.
        if transcript_response is not None:
            transcript_responses.append(transcript_response)

    # 5. 저장된 transcript가 하나도 없으면 요약 생성 불가
    if not transcript_responses:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="모든 오디오 파일의 STT 결과가 비어 있어 회의 요약을 생성할 수 없습니다.",
        )

    # 6. transcript 내용만 추출해서 하나로 합치기
    transcript_texts: list[str] = []

    for transcript_response in transcript_responses:
        content = normalize_transcript_text(transcript_response.content)

        if content:
            transcript_texts.append(content)

    combined_transcript = "\n\n".join(transcript_texts)

    # 7. 최종 combined_transcript 검증
    if not combined_transcript:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="STT 결과가 비어 있어 회의 요약을 생성할 수 없습니다.",
        )

    # 8. LLM 요약 함수 한 번만 호출
    #
    # 현재는 오디오만 처리하므로 ocr_text는 빈 문자열로 전달한다.
    # 나중에 OCR 결과까지 합칠 경우 ocr_text에 이미지 OCR 내용을 넣으면 된다.
    try:
        summary_result = summarize_meeting_from_text(
            stt_text=combined_transcript,
            ocr_text="",
        )

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"회의 요약 생성 중 오류가 발생했습니다: {str(e)}",
        )

    # 9. SummaryCreate 생성
    #
    # SummaryCreate.content가 str 타입이므로,
    # dict 형태인 summary_result를 JSON 문자열로 변환해서 저장한다.
    summary_data = SummaryCreate(
        meeting_id=meeting_id,
        content=safe_json_dumps(summary_result),
    )

    # 10. Summary 1개 DB 저장 또는 갱신
    #
    # create_summary()를 사용하면 같은 meeting_id에 대해
    # summary가 여러 개 생길 수 있으므로 upsert_summary()를 사용한다.
    meeting_summary = upsert_summary(db, summary_data)

    # 11. 최종 summary 응답 반환
    return SummaryResponse.model_validate(meeting_summary)


def process_uploaded_audio_files(
    db: Session,
    meeting_id: int,
    upload_files: list[UploadFile],
    current_user: User,
) -> list[TranscriptResponse]:
    """
    여러 오디오 파일을 처리하고 transcript 목록만 반환한다.

    주의
    ----
    이 함수는 요약을 만들지 않는다.
    파일별 transcript 결과만 필요한 경우에 사용한다.

    현재 앱의 요구사항이
    '여러 오디오 파일 → transcript 저장 → combined_transcript → summary 1개 생성'
    이라면 upload_router에서는 이 함수가 아니라
    process_uploaded_audio_files_and_create_summary()를 사용해야 한다.
    """

    # 1. 파일 목록 확인
    if not upload_files:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드된 오디오 파일이 없습니다.",
        )

    # 2. 현재 로그인한 사용자의 회의인지 확인
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

    responses: list[TranscriptResponse] = []

    # 3. 여러 오디오 파일 처리
    for upload_file in upload_files:
        transcript_response = _process_single_audio_to_transcript(
            db=db,
            meeting_id=meeting_id,
            upload_file=upload_file,
            current_user=current_user,
        )

        # STT 결과가 빈 파일은 저장되지 않으므로 건너뛴다.
        if transcript_response is not None:
            responses.append(transcript_response)

    # 4. 전부 빈 STT 결과라면 에러 처리
    if not responses:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="모든 오디오 파일의 STT 결과가 비어 있어 transcript를 저장할 수 없습니다.",
        )

    return responses