"""
utils/audio_converter.py

역할
- 업로드된 오디오 파일을 STT 서버가 처리하기 쉬운 wav 형식으로 변환한다.
- Android에서 m4a, mp4, aac 등으로 녹음되어도 백엔드에서 wav로 통일한다.
"""

from __future__ import annotations

import subprocess
from pathlib import Path


def convert_audio_to_wav(input_path: str) -> str:
    """
    오디오 파일을 wav 형식으로 변환한다.
    """

    input_file = Path(input_path)

    if not input_file.exists():
        raise FileNotFoundError(f"오디오 파일을 찾을 수 없습니다: {input_path}")

    if input_file.suffix.lower() == ".wav":
        return str(input_file)

    output_file = input_file.with_suffix(".wav")

    command = [
        "ffmpeg",
        "-y",
        "-i",
        str(input_file),
        "-ac",
        "1",
        "-ar",
        "16000",
        str(output_file),
    ]

    try:
        subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=True,
        )
    except FileNotFoundError as e:
        raise RuntimeError(
            "ffmpeg가 설치되어 있지 않습니다. "
            "Windows에서는 ffmpeg 설치 후 PATH 등록이 필요합니다."
        ) from e
    except subprocess.CalledProcessError as e:
        raise RuntimeError(
            f"wav 변환 실패\n"
            f"입력 파일: {input_file}\n"
            f"ffmpeg 에러:\n{e.stderr}"
        ) from e

    return str(output_file)