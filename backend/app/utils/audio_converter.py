"""
utils/audio_converter.py

역할
- 업로드된 오디오 파일을 STT 서버가 처리하기 쉬운 wav 형식으로 변환한다.
- Android에서 m4a, mp4, aac 등으로 녹음되어도 백엔드에서 wav로 통일한다.
- 이미 wav 파일이어도 STT가 더 잘 처리할 수 있도록 16kHz / mono / PCM 16bit로 재인코딩한다.
- 음량 증폭은 프론트 앱에서 처리한다.
- 변환본은 원본 파일과 섞이지 않도록 audio/converted 폴더에 임시 저장한다.
"""

from __future__ import annotations

import subprocess
from pathlib import Path


def convert_audio_to_wav(input_path: str) -> str:
    """
    오디오 파일을 STT 서버용 wav 형식으로 변환한다.

    처리 내용
    - 입력 파일이 wav여도 그대로 반환하지 않는다.
    - 16kHz
    - mono
    - PCM 16bit
    - 음량 증폭은 하지 않는다.

    예시
    - 원본:
      uploads/users/4/meetings/59/audio/test.wav

    - 변환본:
      uploads/users/4/meetings/59/audio/converted/test_stt.wav
    """

    input_file = Path(input_path)

    if not input_file.exists():
        raise FileNotFoundError(f"오디오 파일을 찾을 수 없습니다: {input_path}")

    converted_dir = input_file.parent / "converted"
    converted_dir.mkdir(parents=True, exist_ok=True)

    output_file = converted_dir / f"{input_file.stem}_stt.wav"

    command = [
        "ffmpeg",
        "-y",
        "-i",
        str(input_file),

        # STT용 표준 오디오 형식
        "-ac",
        "1",          # mono
        "-ar",
        "16000",      # 16kHz
        "-c:a",
        "pcm_s16le",  # PCM 16bit little-endian

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
            f"출력 파일: {output_file}\n"
            f"ffmpeg 에러:\n{e.stderr}"
        ) from e

    return str(output_file)