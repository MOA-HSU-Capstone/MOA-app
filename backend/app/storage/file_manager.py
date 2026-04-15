"""
file_manager.py

업로드 파일을 로컬 스토리지에 저장하는 모듈 (meeting_id 기반)

역할
- 업로드된 오디오 파일 저장
- 업로드된 이미지 파일 저장
- 파일명을 UUID 기반으로 변경하여 중복 방지
- meeting_id 기준 폴더 생성 및 관리

저장 구조
---------
uploads/
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


# -----------------------------------------
# 내부 유틸
# -----------------------------------------
def _generate_unique_filename(original_filename: str | None) -> str:
    """
    원본 파일명을 기반으로 UUID를 붙여 고유 파일명 생성
    """

    suffix = ""

    if original_filename:
        suffix = Path(original_filename).suffix.lower()

    return f"{uuid.uuid4().hex}{suffix}"


def _ensure_meeting_dirs(meeting_id: int) -> tuple[str, str]:
    """
    meeting_id 기준 폴더 생성

    Returns
    -------
    (audio_dir, image_dir)
    """

    base_dir = os.path.join("uploads", "meetings", str(meeting_id))
    audio_dir = os.path.join(base_dir, "audio")
    image_dir = os.path.join(base_dir, "images")

    os.makedirs(audio_dir, exist_ok=True)
    os.makedirs(image_dir, exist_ok=True)

    return audio_dir, image_dir


# -----------------------------------------
# 오디오 저장
# -----------------------------------------
def save_audio_file(upload_file, meeting_id: int) -> str:
    """
    업로드된 오디오 파일을 meeting_id 폴더에 저장
    """

    audio_dir, _ = _ensure_meeting_dirs(meeting_id)

    filename = _generate_unique_filename(upload_file.filename)
    save_path = os.path.join(audio_dir, filename)

    with open(save_path, "wb") as buffer:
        shutil.copyfileobj(upload_file.file, buffer)

    return save_path


# -----------------------------------------
# 이미지 저장
# -----------------------------------------
def save_image_file(upload_file, meeting_id: int) -> str:
    """
    업로드된 이미지 파일을 meeting_id 폴더에 저장
    """

    _, image_dir = _ensure_meeting_dirs(meeting_id)

    filename = _generate_unique_filename(upload_file.filename)
    save_path = os.path.join(image_dir, filename)

    with open(save_path, "wb") as buffer:
        shutil.copyfileobj(upload_file.file, buffer)

    return save_path