"""
image_service.py

이미지 처리 서비스 계층

역할
- 업로드된 이미지 파일 저장
- 이미지 전처리
- OCR 수행
- 화이트보드 이미지는 추가 분석
- image DB 저장

흐름
upload_router
    ↓
image_service
    ↓
meeting_repository
    ↓
file_manager
    ↓
preprocess
    ↓
image_ocr
    ↓
image_repository
"""

from __future__ import annotations

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from ai.image_ocr import process_image_by_type
from repositories.image_repository import create_image
from repositories.meeting_repository import get_meeting_by_id
from schemas.image_schema import ImageCreate, ImageResponse, ImageUploadResponse
from storage.file_manager import save_image_file
from utils.pdf_extract import extract_pdf_plain_text
from utils.preprocess import preprocess_image_file


def process_uploaded_image(
    db: Session,
    meeting_id: int,
    upload_file,
    image_type: str = "image",
) -> ImageUploadResponse:
    """
    업로드된 이미지를 저장하고 OCR/분석 후 DB에 저장

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    meeting_id : int
        연결될 회의 ID

    upload_file : UploadFile
        FastAPI UploadFile 객체

    image_type : str
        이미지 종류
        - image
        - whiteboard

    Returns
    -------
    ImageUploadResponse
        업로드 후 OCR/분석 결과를 포함한 응답

    동작 방식
    --------
    0. meeting 존재 여부 확인
    1. 이미지 파일 저장
    2. 이미지 전처리
    3. image_type에 따라 OCR / 분석 수행
    4. image DB 저장
    5. 응답 반환
    """
    # 0. meeting 존재 확인
    meeting = get_meeting_by_id(db, meeting_id)
    if meeting is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="해당 meeting_id의 회의가 없습니다.",
        )

    # 0-1. image_type 방어적 검증
    # upload_router에서 이미 검증하지만, 서비스 계층에서도 한 번 더 확인
    if image_type not in {"image", "whiteboard"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="image_type은 'image' 또는 'whiteboard'만 가능합니다.",
        )

    # 1. 이미지 파일 저장
    # meeting_id를 함께 넘겨 회의별 폴더에 저장되도록 처리
    saved_path = save_image_file(
        upload_file=upload_file,
        meeting_id=meeting_id,
    )

    # 2. PDF는 텍스트 레이어만 추출(스캔 PDF는 비어 있을 수 있음). 그 외(이미지)는 비전 OCR.
    lower = saved_path.lower()
    if lower.endswith(".pdf"):
        try:
            pdf_text = extract_pdf_plain_text(saved_path).strip()
        except Exception:
            pdf_text = ""
        if not pdf_text:
            image_result = {
                "ocr_text": "",
                "analysis_text": (
                    "이 PDF에서 추출할 텍스트가 없습니다. "
                    "스캔 PDF는 '촬영하기'로 페이지를 이미지로 올리거나, 텍스트가 포함된 PDF를 사용해 주세요."
                ),
            }
        else:
            image_result = {"ocr_text": pdf_text, "analysis_text": ""}
        processed_path = saved_path
    else:
        processed_path = preprocess_image_file(saved_path)
        image_result = process_image_by_type(
            image_path=processed_path,
            image_type=image_type,
        )

    # 4. DB 저장용 스키마 생성
    image_data = ImageCreate(
        meeting_id=meeting_id,
        file_path=processed_path,
        image_type=image_type,
        ocr_text=(image_result.get("ocr_text") or "").strip(),
        analysis_text=(image_result.get("analysis_text") or "").strip(),
    )

    image = create_image(db, image_data)

    # 5. 업로드 응답 반환
    return ImageUploadResponse(
        meeting_id=image.meeting_id,
        file_path=image.file_path,
        image_type=image.image_type,
        ocr_text=image.ocr_text,
        analysis_text=image.analysis_text,
    )


def get_meeting_images(
    db: Session,
    meeting_id: int,
) -> list[ImageResponse]:
    """
    특정 회의의 이미지 목록을 조회하는 서비스

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    meeting_id : int
        회의 ID

    Returns
    -------
    list[ImageResponse]
        해당 회의에 연결된 이미지 목록
    """

    from repositories.image_repository import get_images_by_meeting_id

    images = get_images_by_meeting_id(db, meeting_id)

    return [ImageResponse.model_validate(image) for image in images]