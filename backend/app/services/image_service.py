"""
image_service.py

이미지/PDF 처리 서비스 계층

역할
- 업로드된 이미지 또는 PDF 파일 저장
- 업로드된 이미지/PDF 파일 메타데이터 uploaded_files 테이블에 저장
- 이미지 전처리
- PDF 파일은 페이지별 이미지로 변환
- OCR 수행
- 화이트보드 이미지는 추가 분석
- image DB 저장
- 여러 이미지/PDF 파일 처리

흐름
upload_router
    ↓
image_service
    ↓
meeting_repository
    ↓
file_manager
    ↓
uploaded_file_repository
    ↓
preprocess / pdf_converter
    ↓
image_ocr
    ↓
image_repository

현재 저장 구조
-------------
uploads/
└─ users/
    └─ {user_id}/
        └─ meetings/
            └─ {meeting_id}/
                ├─ audio/
                └─ images/

PDF 처리 방식
-------------
PDF 원본 저장
→ PDF를 페이지별 PNG로 변환
→ 각 페이지 이미지 OCR
→ OCR 결과를 합쳐서 image 테이블에 1개 row로 저장
→ uploaded_files 테이블에는 PDF 원본 파일 정보 저장
"""

from __future__ import annotations

import mimetypes
from pathlib import Path

from fastapi import HTTPException, UploadFile, status
from sqlalchemy.orm import Session

from ai.image_ocr import process_image_by_type
from models.user_model import User
from repositories.image_repository import create_image
from repositories.meeting_repository import get_meeting_by_id_and_user_id
from repositories.uploaded_file_repository import create_uploaded_file
from schemas.image_schema import ImageCreate, ImageResponse, ImageUploadResponse
from schemas.uploaded_file_schema import UploadedFileCreate
from storage.file_manager import save_image_file
from utils.pdf_converter import convert_pdf_to_images
from utils.preprocess import preprocess_image_file


def _get_file_size_bytes(file_path: str) -> int | None:
    """
    파일 크기를 byte 단위로 반환한다.

    파일이 없거나 확인할 수 없는 경우 None을 반환한다.
    """

    try:
        return Path(file_path).stat().st_size
    except Exception:
        return None


def _get_original_filename(upload_file: UploadFile) -> str:
    """
    업로드 원본 파일명을 안전하게 추출한다.

    클라이언트에 따라 파일 경로가 섞여 들어올 가능성이 있으므로
    Path(...).name으로 파일명만 사용한다.
    """

    return Path(upload_file.filename or "unknown_image_file").name


def _create_image_file_metadata(
    db: Session,
    meeting_id: int,
    upload_file: UploadFile,
    saved_path: str,
    file_type: str,
) -> None:
    """
    uploaded_files 테이블에 이미지/PDF 파일 메타데이터를 저장한다.

    file_type
    ---------
    - image
    - pdf
    """

    create_uploaded_file(
        db=db,
        file_data=UploadedFileCreate(
            meeting_id=meeting_id,
            original_name=_get_original_filename(upload_file),
            saved_path=str(saved_path),
            file_type=file_type,
            mime_type=upload_file.content_type,
            size_bytes=_get_file_size_bytes(saved_path),
        ),
    )


def _is_pdf_file(file_path: str, upload_file: UploadFile | None = None) -> bool:
    """
    업로드된 파일이 PDF인지 확인한다.

    확인 기준
    --------
    1. UploadFile.content_type
    2. 확장자 / mimetype
    3. 파일 헤더(%PDF)
    """

    if upload_file is not None and upload_file.content_type == "application/pdf":
        return True

    mime_type, _ = mimetypes.guess_type(file_path)

    if mime_type == "application/pdf":
        return True

    if Path(file_path).suffix.lower() == ".pdf":
        return True

    try:
        with open(file_path, "rb") as file:
            header = file.read(4)

        return header == b"%PDF"

    except FileNotFoundError:
        raise

    except Exception:
        return False


def _process_pdf_file(
    db: Session,
    meeting_id: int,
    saved_path: str,
    image_type: str,
) -> ImageUploadResponse:
    """
    PDF 파일 1개를 처리한다.

    동작 방식
    --------
    1. PDF를 페이지별 PNG 이미지로 변환
    2. 각 페이지 이미지를 전처리
    3. 각 페이지 이미지 OCR 수행
    4. OCR/분석 결과를 합쳐서 image 테이블에 1개 row로 저장
    """

    try:
        pdf_output_dir = str(Path(saved_path).with_suffix(""))

        page_image_paths = convert_pdf_to_images(
            pdf_path=saved_path,
            output_dir=pdf_output_dir,
            dpi=200,
        )

    except FileNotFoundError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"PDF 파일을 찾을 수 없습니다: {str(e)}",
        )

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=(
                "PDF를 이미지로 변환하는 중 오류가 발생했습니다. "
                "서버에 poppler-utils가 설치되어 있는지 확인하세요. "
                f"원인: {str(e)}"
            ),
        )

    if not page_image_paths:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="PDF에서 변환된 페이지 이미지가 없습니다.",
        )

    page_ocr_texts: list[str] = []
    page_analysis_texts: list[str] = []

    for page_index, page_image_path in enumerate(page_image_paths, start=1):
        try:
            processed_page_path = preprocess_image_file(page_image_path)

            image_result = process_image_by_type(
                image_path=processed_page_path,
                image_type=image_type,
            )

        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"PDF {page_index}페이지 OCR 처리 중 오류가 발생했습니다: {str(e)}",
            )

        ocr_text = (image_result.get("ocr_text") or "").strip()
        analysis_text = (image_result.get("analysis_text") or "").strip()

        if ocr_text:
            page_ocr_texts.append(
                f"[PDF {page_index}페이지 OCR]\n{ocr_text}"
            )

        if analysis_text:
            page_analysis_texts.append(
                f"[PDF {page_index}페이지 분석]\n{analysis_text}"
            )

    merged_ocr_text = "\n\n".join(page_ocr_texts).strip()
    merged_analysis_text = "\n\n".join(page_analysis_texts).strip()

    image_data = ImageCreate(
        meeting_id=meeting_id,
        file_path=saved_path,
        image_type="pdf",
        ocr_text=merged_ocr_text,
        analysis_text=merged_analysis_text,
    )

    image = create_image(db, image_data)

    return ImageUploadResponse(
        meeting_id=image.meeting_id,
        file_path=image.file_path,
        image_type=image.image_type,
        ocr_text=image.ocr_text,
        analysis_text=image.analysis_text,
    )


def _process_image_file(
    db: Session,
    meeting_id: int,
    saved_path: str,
    image_type: str,
) -> ImageUploadResponse:
    """
    일반 이미지 파일 1개를 처리한다.

    동작 방식
    --------
    1. 이미지 전처리
    2. OCR / 화이트보드 분석 수행
    3. image DB 저장
    4. ImageUploadResponse 반환
    """

    try:
        processed_path = preprocess_image_file(saved_path)

    except FileNotFoundError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"이미지 파일을 찾을 수 없습니다: {str(e)}",
        )

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"이미지 전처리 중 오류가 발생했습니다: {str(e)}",
        )

    try:
        image_result = process_image_by_type(
            image_path=processed_path,
            image_type=image_type,
        )

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"이미지 OCR 처리 중 오류가 발생했습니다: {str(e)}",
        )

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


def _process_single_image(
    db: Session,
    meeting_id: int,
    upload_file: UploadFile,
    current_user: User,
    image_type: str = "image",
) -> ImageUploadResponse:
    """
    이미지 또는 PDF 파일 1개를 처리해서 image DB에 저장한다.

    이미지인 경우
    ------------
    저장 → uploaded_files 저장 → 전처리 → OCR → image DB 저장

    PDF인 경우
    ---------
    저장 → uploaded_files 저장 → 페이지별 이미지 변환 → 페이지별 OCR → 결과 합치기 → image DB 저장
    """

    if not upload_file.filename:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="파일명이 비어 있는 파일이 포함되어 있습니다.",
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

    is_pdf = _is_pdf_file(
        file_path=saved_path,
        upload_file=upload_file,
    )

    if is_pdf:
        # PDF 원본 파일 메타데이터 저장
        _create_image_file_metadata(
            db=db,
            meeting_id=meeting_id,
            upload_file=upload_file,
            saved_path=saved_path,
            file_type="pdf",
        )

        return _process_pdf_file(
            db=db,
            meeting_id=meeting_id,
            saved_path=saved_path,
            image_type=image_type,
        )

    # 이미지 원본 파일 메타데이터 저장
    _create_image_file_metadata(
        db=db,
        meeting_id=meeting_id,
        upload_file=upload_file,
        saved_path=saved_path,
        file_type="image",
    )

    return _process_image_file(
        db=db,
        meeting_id=meeting_id,
        saved_path=saved_path,
        image_type=image_type,
    )


def process_uploaded_image(
    db: Session,
    meeting_id: int,
    upload_file: UploadFile,
    current_user: User,
    image_type: str = "image",
) -> ImageUploadResponse:
    """
    이미지/PDF 파일 1개 업로드 처리 함수.

    기존 단일 이미지 업로드 API가 필요할 수 있으므로 유지한다.

    동작 방식
    --------
    1. 현재 로그인한 사용자의 회의인지 확인
    2. 이미지 또는 PDF 파일 1개 처리
    3. uploaded_files DB 저장
    4. image DB 저장
    5. ImageUploadResponse 반환
    """

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
    여러 이미지/PDF 파일을 순서대로 처리한다.

    역할
    ----
    - upload_router에서 files: list[UploadFile]로 받은 파일 목록을 처리
    - 현재 로그인한 사용자의 회의인지 확인
    - 각 파일을 사용자/회의별 폴더에 저장
    - uploaded_files에 파일 메타데이터 저장
    - 이미지 파일이면 기존 OCR 수행
    - PDF 파일이면 페이지별 이미지 변환 후 OCR 수행
    - 이미지별 image DB 저장
    - ImageUploadResponse 목록 반환
    """

    if not upload_files:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드된 이미지/PDF 파일이 없습니다.",
        )

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

    if image_type not in {"image", "whiteboard"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="image_type은 'image' 또는 'whiteboard'만 가능합니다.",
        )

    responses: list[ImageUploadResponse] = []

    for upload_file in upload_files:
        response = _process_single_image(
            db=db,
            meeting_id=meeting_id,
            upload_file=upload_file,
            current_user=current_user,
            image_type=image_type,
        )

        responses.append(response)

    return responses


def get_meeting_images(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> list[ImageResponse]:
    """
    특정 회의의 이미지/PDF OCR 결과 목록을 조회하는 서비스

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    meeting_id : int
        회의 ID

    current_user : User
        현재 로그인한 사용자

    Returns
    -------
    list[ImageResponse]
        해당 회의에 연결된 이미지/PDF OCR 결과 목록
    """

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

    from repositories.image_repository import get_images_by_meeting_id

    images = get_images_by_meeting_id(db, meeting_id)

    return [ImageResponse.model_validate(image) for image in images]