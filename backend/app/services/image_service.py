"""
image_service.py

이미지 처리 서비스 계층

역할
- 업로드된 이미지 파일 저장
- 이미지 전처리
- OCR 수행
- 화이트보드 이미지는 추가 분석
- image DB 저장
- 여러 이미지 파일 처리

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
from repositories.image_repository import create_image
from repositories.meeting_repository import get_meeting_by_id_and_user_id
from schemas.image_schema import ImageCreate, ImageResponse, ImageUploadResponse
from storage.file_manager import save_image_file
from utils.preprocess import preprocess_image_file


def _process_single_image(
    db: Session,
    meeting_id: int,
    upload_file: UploadFile,
    current_user: User,
    image_type: str = "image",
) -> ImageUploadResponse:
    """
    이미지 파일 1개를 처리해서 image DB에 저장한다.

    이 함수는 내부 재사용용 함수이다.

    동작 방식
    --------
    1. 파일명 확인
    2. image_type 검증
    3. 현재 로그인한 사용자 기준 폴더에 이미지 파일 저장
    4. 이미지 전처리
    5. OCR / 화이트보드 분석 수행
    6. OCR / 분석 결과 정리
    7. image DB 저장
    8. ImageUploadResponse 반환
    """

    # 1. 파일명 확인
    if not upload_file.filename:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="파일명이 비어 있는 이미지 파일이 포함되어 있습니다.",
        )

    # 2. image_type 검증
    if image_type not in {"image", "whiteboard"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="image_type은 'image' 또는 'whiteboard'만 가능합니다.",
        )

    # 3. 이미지 파일 저장
    #
    # 저장 예시:
    # uploads/users/{user_id}/meetings/{meeting_id}/images/{uuid}.png
    saved_path = save_image_file(
        upload_file=upload_file,
        user_id=current_user.id,
        meeting_id=meeting_id,
    )

    # 4. 이미지 전처리
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

    # 5. 이미지 종류에 따라 OCR / 분석 수행
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

    # 6. OCR / 분석 결과 정리
    ocr_text = (image_result.get("ocr_text") or "").strip()
    analysis_text = (image_result.get("analysis_text") or "").strip()

    # 7. DB 저장용 스키마 생성
    image_data = ImageCreate(
        meeting_id=meeting_id,
        file_path=processed_path,
        image_type=image_type,
        ocr_text=ocr_text,
        analysis_text=analysis_text,
    )

    # 8. image DB 저장
    image = create_image(db, image_data)

    # 9. 업로드 응답 반환
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

    기존 단일 이미지 업로드 API가 필요할 수 있으므로 유지한다.

    동작 방식
    --------
    1. 현재 로그인한 사용자의 회의인지 확인
    2. 이미지 파일 1개 처리
    3. image DB 저장
    4. ImageUploadResponse 반환
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

    # 2. 이미지 파일 1개 처리
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
    여러 이미지 파일을 순서대로 처리한다.

    역할
    ----
    - upload_router에서 files: list[UploadFile]로 받은 이미지 목록을 처리
    - 현재 로그인한 사용자의 회의인지 확인
    - 각 이미지 파일을 사용자/회의별 폴더에 저장
    - 각 이미지 파일에 대해 OCR / 화이트보드 분석 수행
    - 이미지별 image DB 저장
    - ImageUploadResponse 목록 반환

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    meeting_id : int
        이미지 파일들이 연결될 회의 ID

    upload_files : list[UploadFile]
        FastAPI UploadFile 객체 목록

    current_user : User
        현재 로그인한 사용자

    image_type : str
        이미지 종류
        - image
        - whiteboard

    Returns
    -------
    list[ImageUploadResponse]
        각 이미지 파일 처리 결과 목록
    """

    # 1. 파일 목록 확인
    if not upload_files:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="업로드된 이미지 파일이 없습니다.",
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

    # 3. image_type 검증
    if image_type not in {"image", "whiteboard"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="image_type은 'image' 또는 'whiteboard'만 가능합니다.",
        )

    # 4. 각 이미지 처리 결과를 담을 리스트
    responses: list[ImageUploadResponse] = []

    # 5. 여러 이미지 파일을 하나씩 처리
    for upload_file in upload_files:
        response = _process_single_image(
            db=db,
            meeting_id=meeting_id,
            upload_file=upload_file,
            current_user=current_user,
            image_type=image_type,
        )

        responses.append(response)

    # 6. 여러 이미지 처리 결과 반환
    return responses


def get_meeting_images(
    db: Session,
    meeting_id: int,
    current_user: User,
) -> list[ImageResponse]:
    """
    특정 회의의 이미지 목록을 조회하는 서비스

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
        해당 회의에 연결된 이미지 목록
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

    # 2. 이미지 목록 조회
    from repositories.image_repository import get_images_by_meeting_id

    images = get_images_by_meeting_id(db, meeting_id)

    return [ImageResponse.model_validate(image) for image in images]