"""
image_service.py

이미지 처리 서비스 계층

역할
- 업로드된 이미지/문서 파일 저장
- 이미지 전처리
- OCR 수행
- PDF 텍스트 레이어 추출
- image DB 저장
- 여러 이미지 파일 처리

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

from ai.image_ocr import process_image_by_type
from models.user_model import User
from repositories.image_repository import create_image, get_images_by_meeting_id
from repositories.meeting_repository import get_meeting_by_id_and_user_id
from schemas.image_schema import ImageCreate, ImageResponse, ImageUploadResponse
from storage.file_manager import save_image_file
from utils.pdf_extract import extract_pdf_plain_text
from utils.preprocess import preprocess_image_file


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


def _extract_pdf_result(saved_path: str) -> dict[str, str]:
    try:
        pdf_text = extract_pdf_plain_text(saved_path).strip()
    except Exception:
        pdf_text = ""

    if not pdf_text:
        return {
            "ocr_text": "",
            "analysis_text": (
                "이 PDF에서 추출할 텍스트가 없습니다. "
                "스캔 PDF는 '촬영하기'로 페이지를 이미지로 올리거나, 텍스트가 포함된 PDF를 사용해 주세요."
            ),
        }

    return {"ocr_text": pdf_text, "analysis_text": ""}


def _process_single_image(
    db: Session,
    meeting_id: int,
    upload_file: UploadFile,
    current_user: User,
    image_type: str = "image",
) -> ImageUploadResponse:
    """
    이미지/문서 파일 1개를 처리해서 image DB에 저장한다.
    """

    if not upload_file.filename:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="파일명이 비어 있는 이미지 파일이 포함되어 있습니다.",
        )

    if image_type not in {"image", "whiteboard"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="image_type은 'image' 또는 'whiteboard'만 가능합니다.",
        )

    saved_path = save_image_file(
        upload_file=upload_file,
        user_id=current_user.id,
        meeting_id=meeting_id,
    )

    lower_path = saved_path.lower()
    if lower_path.endswith(".pdf"):
        processed_path = saved_path
        image_result = _extract_pdf_result(saved_path)
    else:
        try:
            processed_path = preprocess_image_file(saved_path)
        except FileNotFoundError as e:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"이미지 파일을 찾을 수 없습니다: {str(e)}",
            ) from e
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"이미지 전처리 중 오류가 발생했습니다: {str(e)}",
            ) from e

        try:
            image_result = process_image_by_type(
                image_path=processed_path,
                image_type=image_type,
            )
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"이미지 OCR 처리 중 오류가 발생했습니다: {str(e)}",
            ) from e

    ocr_text = (image_result.get("ocr_text") or "").strip()
    analysis_text = (image_result.get("analysis_text") or "").strip()

    image_data = ImageCreate(
        meeting_id=meeting_id,
        file_path=processed_path,
        image_type=image_type,
        ocr_text=ocr_text,
        analysis_text=analysis_text,
    )

    image = create_image(db, image_data)

    return ImageUploadResponse(
        meeting_id=image.meeting_id,
        file_path=image.file_path,
        image_type=image.image_type,
        ocr_text=image.ocr_text,
        analysis_text=image.analysis_text,
    )


def process_uploaded_image(
    db: Session,
    meeting_id: int,
    upload_file: UploadFile,
    current_user: User,
    image_type: str = "image",
) -> ImageUploadResponse:
    """
    이미지 파일 1개 업로드 처리 함수.
    """

    _ensure_user_meeting(db=db, meeting_id=meeting_id, current_user=current_user)

    return _process_single_image(
        db=db,
        meeting_id=meeting_id,
        upload_file=upload_file,
        current_user=current_user,
        image_type=image_type,
    )


def process_uploaded_image_files(
    db: Session,
    meeting_id: int,
    upload_files: list[UploadFile],
    current_user: User,
    image_type: str = "image",
) -> list[ImageUploadResponse]:
    """
    여러 이미지/문서 파일을 순서대로 처리한다.
    """

    if not upload_files:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드된 이미지 파일이 없습니다.",
        )

    _ensure_user_meeting(db=db, meeting_id=meeting_id, current_user=current_user)

    if image_type not in {"image", "whiteboard"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="image_type은 'image' 또는 'whiteboard'만 가능합니다.",
        )

    responses: list[ImageUploadResponse] = []
    for upload_file in upload_files:
        responses.append(
            _process_single_image(
                db=db,
                meeting_id=meeting_id,
                upload_file=upload_file,
                current_user=current_user,
                image_type=image_type,
            ),
        )

    return responses


def get_meeting_images(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> list[ImageResponse]:
    """
    특정 회의의 이미지 목록을 조회한다.
    """

    _ensure_user_meeting(db=db, meeting_id=meeting_id, current_user=current_user)

    images = get_images_by_meeting_id(db, meeting_id)
    return [ImageResponse.model_validate(image) for image in images]
