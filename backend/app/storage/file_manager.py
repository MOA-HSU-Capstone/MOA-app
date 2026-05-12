"""
file_manager.py

업로드 파일을 로컬 스토리지에 저장하는 모듈 (user_id + meeting_id 기반)

역할
- 업로드된 오디오 파일 저장
- 업로드된 이미지 파일 저장
- 파일명을 UUID 기반으로 변경하여 중복 방지
- user_id + meeting_id 기준 폴더 생성 및 관리

저장 구조
---------
uploads/
└─ users/
    └─ {user_id}/
        └─ meetings/
            └─ {meeting_id}/
                ├─ audio/
                └─ images/
"""

from __future__ import annotations

import os
import shutil
import uuid
from pathlib import Path

from storage.upload_paths import ensure_user_meeting_upload_dirs


# -----------------------------------------
# 내부 유틸
# -----------------------------------------
def _generate_unique_filename(original_filename: str | None) -> str:
    """
    원본 파일명을 기반으로 UUID 파일명 생성

    원본 파일명 자체는 저장하지 않고,
    확장자만 유지해서 파일명 충돌과 한글/공백 문제를 줄인다.

    예시
    ----
    원본 파일명: meeting.wav
    저장 파일명: 9f3a1c2b...wav
    """

    suffix = ""

    if original_filename:
        suffix = Path(original_filename).suffix.lower()

    return f"{uuid.uuid4().hex}{suffix}"


# -----------------------------------------
# 오디오 저장
# -----------------------------------------
def save_audio_file(
    upload_file,
    user_id: int,
    meeting_id: int,
) -> str:
    """
    업로드된 오디오 파일을 user_id + meeting_id 기준 audio 폴더에 저장

    저장 예시
    --------
    uploads/users/1/meetings/3/audio/{uuid}.wav

    Parameters
    ----------
    upload_file
        FastAPI UploadFile 객체

    user_id : int
        현재 로그인한 사용자 ID

    meeting_id : int
        업로드 대상 회의 ID

    Returns
    -------
    str
        저장된 파일 경로
    """

    # 사용자/회의별 audio, images 폴더 생성
    audio_dir, _ = ensure_user_meeting_upload_dirs(
        user_id=user_id,
        meeting_id=meeting_id,
    )

    # UUID 기반 파일명 생성
    filename = _generate_unique_filename(upload_file.filename)

    # 최종 저장 경로
    save_path = os.path.join(audio_dir, filename)

    # 업로드 파일을 로컬 디스크에 저장
    with open(save_path, "wb") as buffer:
        shutil.copyfileobj(upload_file.file, buffer)

    return save_path


# -----------------------------------------
# 이미지 저장
# -----------------------------------------
def save_image_file(
    upload_file,
    user_id: int,
    meeting_id: int,
) -> str:
    """
    업로드된 이미지 파일을 user_id + meeting_id 기준 images 폴더에 저장

    저장 예시
    --------
    uploads/users/1/meetings/3/images/{uuid}.png

    Parameters
    ----------
    upload_file
        FastAPI UploadFile 객체

    user_id : int
        현재 로그인한 사용자 ID

    meeting_id : int
        업로드 대상 회의 ID

    Returns
    -------
    str
        저장된 파일 경로
    """

    # 사용자/회의별 audio, images 폴더 생성
    _, image_dir = ensure_user_meeting_upload_dirs(
        user_id=user_id,
        meeting_id=meeting_id,
    )

    # UUID 기반 파일명 생성
    filename = _generate_unique_filename(upload_file.filename)

    # 최종 저장 경로
    save_path = os.path.join(image_dir, filename)

    # 업로드 파일을 로컬 디스크에 저장
    with open(save_path, "wb") as buffer:
        shutil.copyfileobj(upload_file.file, buffer)

    return save_path