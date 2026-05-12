"""
upload_router.py

파일 업로드 관련 API 라우터

역할
- 오디오 여러 개 업로드
- 이미지 여러 개 업로드

주의
- 실제 파일 저장, 전처리, STT/OCR/분석, DB 저장은 services 계층에서 처리
- 이 파일은 업로드 요청과 응답 처리에 집중
- 로그인 기능이 있으므로 current_user 기준으로 업로드 대상 회의 소유권을 확인해야 함
"""

from typing import List

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from sqlalchemy.orm import Session

from config.database import get_db
from models.user_model import User
from schemas.image_schema import ImageUploadResponse
from schemas.summary_schema import SummaryResponse
from services.audio_service import process_uploaded_audio_files_and_create_summary
from services.image_service import process_uploaded_image_files
from utils.auth_dependency import get_current_user


router = APIRouter(
    prefix="/upload",
    tags=["Uploads"],
)


@router.post(
    "/audio/{meeting_id}",
    response_model=SummaryResponse,
    status_code=status.HTTP_201_CREATED,
    summary="오디오 여러 개 업로드 후 통합 요약 생성",
)
def upload_audio(
    meeting_id: int,
    files: List[UploadFile] = File(..., description="업로드할 오디오 파일 목록"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> SummaryResponse:
    """
    여러 오디오 파일을 업로드하고,
    각 파일마다 STT를 수행하여 transcript를 저장한 뒤,
    전체 transcript를 합쳐 LLM 요약을 한 번만 수행합니다.

    요청 형식
    ----------
    Content-Type: multipart/form-data

    key 이름
    ----------
    files

    인증
    ----------
    Authorization: Bearer {access_token}

    저장 구조
    ----------
    uploads/users/{user_id}/meetings/{meeting_id}/audio/
    """

    if not files:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드할 오디오 파일이 없습니다.",
        )

    for file in files:
        if not file.filename:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="업로드할 오디오 파일명이 비어 있습니다.",
            )

    return process_uploaded_audio_files_and_create_summary(
        db=db,
        meeting_id=meeting_id,
        upload_files=files,
        current_user=current_user,
    )


@router.post(
    "/image/{meeting_id}",
    response_model=List[ImageUploadResponse],
    status_code=status.HTTP_201_CREATED,
    summary="이미지 여러 개 업로드",
)
def upload_image(
    meeting_id: int,
    files: List[UploadFile] = File(..., description="업로드할 이미지 파일 목록"),
    image_type: str = Form("image", description="image 또는 whiteboard"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> List[ImageUploadResponse]:
    """
    이미지 파일 여러 개를 업로드하고 OCR/분석을 수행한 뒤 결과를 저장합니다.

    요청 형식
    ----------
    Content-Type: multipart/form-data

    key 이름
    ----------
    files      : 이미지 파일 여러 개
    image_type : image 또는 whiteboard

    인증
    ----------
    Authorization: Bearer {access_token}

    저장 구조
    ----------
    uploads/users/{user_id}/meetings/{meeting_id}/images/
    """

    if not files:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드할 이미지 파일이 없습니다.",
        )

    for file in files:
        if not file.filename:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="업로드할 이미지 파일명이 비어 있습니다.",
            )

    if image_type not in {"image", "whiteboard"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="image_type은 'image' 또는 'whiteboard'만 가능합니다.",
        )

    return process_uploaded_image_files(
        db=db,
        meeting_id=meeting_id,
        upload_files=files,
        current_user=current_user,
        image_type=image_type,
    )