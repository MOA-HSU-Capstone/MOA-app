"""
file_manager.py

업로드 파일을 로컬 스토리지에 저장하는 모듈 (user_id + meeting_id 기반)

역할
- 업로드된 오디오 파일 저장
- 업로드된 이미지 파일 저장
- 업로드 시 원본 파일명을 유지하되, 동일 폴더 충돌 시에만 번호 접미사로 구분
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
import re
import shutil
from pathlib import Path

from storage.upload_paths import ensure_user_meeting_upload_dirs


# -----------------------------------------
# 내부 유틸
# -----------------------------------------
def _sanitize_client_filename(name: str | None) -> str:
    """
    클라이언트가 보낸 파일명에서 경로 요소·OS 금지 문자만 제거·치환한다.
    """

    if not name or not name.strip():
        return "upload"

    base = Path(name).name
    if not base or base in (".", ".."):
        return "upload"

    base = base.replace("\x00", "")
    base = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", base)
    base = base.strip(" .")
    return base if base else "upload"


def _unique_save_path(directory: str, filename: str) -> str:
    """directory 안에서 filename이 겹치지 않는 전체 경로를 반환한다."""

    path = os.path.join(directory, filename)
    if not os.path.exists(path):
        return path

    stem, ext = os.path.splitext(filename)
    n = 1
    while True:
        candidate = os.path.join(directory, f"{stem}_{n}{ext}")
        if not os.path.exists(candidate):
            return candidate
        n += 1


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
    uploads/users/1/meetings/3/audio/meeting.wav

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

    filename = _sanitize_client_filename(upload_file.filename)
    save_path = _unique_save_path(audio_dir, filename)

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
    uploads/users/1/meetings/3/images/whiteboard.png

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

    filename = _sanitize_client_filename(upload_file.filename)
    save_path = _unique_save_path(image_dir, filename)

    # 업로드 파일을 로컬 디스크에 저장
    with open(save_path, "wb") as buffer:
        shutil.copyfileobj(upload_file.file, buffer)

    return save_path