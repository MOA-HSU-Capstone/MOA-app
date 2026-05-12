"""
upload_paths.py

업로드 파일 저장 경로를 관리하는 모듈 (user_id + meeting_id 기반)

역할
- 업로드 기본 디렉토리 경로 관리
- 사용자별 업로드 디렉토리 경로 생성
- 사용자별 회의 업로드 디렉토리 경로 생성
- 회의별 오디오/이미지 저장 디렉토리 경로 생성
- 폴더가 없으면 자동 생성

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

from config.settings import settings


# -----------------------------------------
# 기본 업로드 디렉토리
# -----------------------------------------

# 기본 업로드 디렉토리
# 예: uploads
BASE_UPLOAD_DIR = settings.upload_dir

# 사용자별 업로드를 모아두는 상위 디렉토리
# 예: uploads/users
USERS_UPLOAD_DIR = os.path.join(BASE_UPLOAD_DIR, "users")


# -----------------------------------------
# 경로 반환 함수
# -----------------------------------------
def get_user_upload_dir(user_id: int) -> str:
    """
    user_id 기준 기본 업로드 폴더 경로 반환

    예시
    ----
    uploads/users/1
    """

    return os.path.join(USERS_UPLOAD_DIR, str(user_id))


def get_user_meetings_dir(user_id: int) -> str:
    """
    user_id 기준 meetings 폴더 경로 반환

    예시
    ----
    uploads/users/1/meetings
    """

    return os.path.join(get_user_upload_dir(user_id), "meetings")


def get_user_meeting_upload_dir(user_id: int, meeting_id: int) -> str:
    """
    user_id + meeting_id 기준 회의 업로드 폴더 경로 반환

    예시
    ----
    uploads/users/1/meetings/3
    """

    return os.path.join(
        get_user_meetings_dir(user_id),
        str(meeting_id),
    )


def get_user_meeting_audio_dir(user_id: int, meeting_id: int) -> str:
    """
    user_id + meeting_id 기준 오디오 저장 폴더 경로 반환

    예시
    ----
    uploads/users/1/meetings/3/audio
    """

    return os.path.join(
        get_user_meeting_upload_dir(user_id, meeting_id),
        "audio",
    )


def get_user_meeting_image_dir(user_id: int, meeting_id: int) -> str:
    """
    user_id + meeting_id 기준 이미지 저장 폴더 경로 반환

    예시
    ----
    uploads/users/1/meetings/3/images
    """

    return os.path.join(
        get_user_meeting_upload_dir(user_id, meeting_id),
        "images",
    )


# -----------------------------------------
# 폴더 생성 함수
# -----------------------------------------
def ensure_base_upload_dirs() -> None:
    """
    기본 업로드 폴더 생성

    앱 시작 시 호출하면 된다.

    생성 구조
    --------
    uploads/
    └─ users/

    주의
    ----
    user_id, meeting_id별 세부 폴더는 실제 업로드 시 생성한다.
    """

    os.makedirs(BASE_UPLOAD_DIR, exist_ok=True)
    os.makedirs(USERS_UPLOAD_DIR, exist_ok=True)


def ensure_user_meeting_upload_dirs(
    user_id: int,
    meeting_id: int,
) -> tuple[str, str]:
    """
    user_id + meeting_id 기준 오디오/이미지 폴더 생성

    생성 구조
    --------
    uploads/
    └─ users/
        └─ {user_id}/
            └─ meetings/
                └─ {meeting_id}/
                    ├─ audio/
                    └─ images/

    Returns
    -------
    (audio_dir, image_dir)
    """

    audio_dir = get_user_meeting_audio_dir(user_id, meeting_id)
    image_dir = get_user_meeting_image_dir(user_id, meeting_id)

    os.makedirs(audio_dir, exist_ok=True)
    os.makedirs(image_dir, exist_ok=True)

    return audio_dir, image_dir