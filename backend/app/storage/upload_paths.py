"""
upload_paths.py

업로드 파일 저장 경로를 관리하는 모듈 (meeting_id 기반)

역할
- 업로드 기본 디렉토리 경로 관리
- 회의별 업로드 디렉토리 경로 생성
- 회의별 오디오/이미지 저장 디렉토리 경로 생성
- 폴더가 없으면 자동 생성

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

from config.settings import settings


# -----------------------------------------
# 기본 업로드 디렉토리
# -----------------------------------------
BASE_UPLOAD_DIR = settings.upload_dir

# 회의별 업로드를 모아두는 상위 디렉토리
MEETINGS_UPLOAD_DIR = os.path.join(BASE_UPLOAD_DIR, "meetings")


def get_meeting_upload_dir(meeting_id: int) -> str:
    """
    meeting_id 기준 기본 폴더 경로 반환

    예시
    ----
    uploads/meetings/1
    """

    return os.path.join(MEETINGS_UPLOAD_DIR, str(meeting_id))


def get_meeting_audio_dir(meeting_id: int) -> str:
    """
    meeting_id 기준 오디오 저장 폴더 경로 반환

    예시
    ----
    uploads/meetings/1/audio
    """

    return os.path.join(get_meeting_upload_dir(meeting_id), "audio")


def get_meeting_image_dir(meeting_id: int) -> str:
    """
    meeting_id 기준 이미지 저장 폴더 경로 반환

    예시
    ----
    uploads/meetings/1/images
    """

    return os.path.join(get_meeting_upload_dir(meeting_id), "images")


def ensure_base_upload_dirs() -> None:
    """
    기본 업로드 폴더 생성

    앱 시작 시 호출하면 됨.
    단, meeting_id별 audio/images 폴더는 실제 업로드 시 생성.
    """

    os.makedirs(BASE_UPLOAD_DIR, exist_ok=True)
    os.makedirs(MEETINGS_UPLOAD_DIR, exist_ok=True)


def ensure_meeting_upload_dirs(meeting_id: int) -> tuple[str, str]:
    """
    meeting_id 기준 오디오/이미지 폴더 생성

    Returns
    -------
    (audio_dir, image_dir)
    """

    audio_dir = get_meeting_audio_dir(meeting_id)
    image_dir = get_meeting_image_dir(meeting_id)

    os.makedirs(audio_dir, exist_ok=True)
    os.makedirs(image_dir, exist_ok=True)

    return audio_dir, image_dir
