"""
stt_client.py

외부 STT 서버와 통신하는 클라이언트 모듈

역할
- wav 오디오 파일을 외부 STT 서버에 업로드
- 업로드 직후 반환되는 task_id 수신
- task_id로 결과를 반복 조회(polling)
- 최종 transcript 텍스트를 반환

현재 STT 서버 방식
-------------------
팀원 서버는 "비동기 처리 방식"으로 동작한다.

1. POST /transcribe
   -> {"task_id": "..."} 반환

2. GET /result/{task_id}
   -> 처리 중이면 {"status": "processing"}
   -> 완료되면 {"text": "..."} 또는 {"status": "done", "text": "..."}
   -> 실패하면 {"status": "error", "message": "..."}

중요
----
- 이 클라이언트는 STT 서버에 wav 파일을 보내는 것을 기준으로 작성됨
- wav 변환은 audio_service.py에서 수행한다.
- 즉, audio_service.py에서 convert_audio_to_wav()를 실행한 뒤,
  변환된 wav 파일 경로를 request_stt() 또는 transcribe_audio_file()에 넘겨야 한다.
"""

from __future__ import annotations

import mimetypes
import os
import time
from pathlib import Path
from typing import Any

import requests

from config.settings import settings


def _get_base_url() -> str:
    """
    settings에서 STT 서버 base URL을 가져온다.

    예:
    http://34.47.117.201:5000
    """

    base_url = (settings.stt_server_url or "").rstrip("/")

    if not base_url:
        raise ValueError("STT_SERVER_URL이 설정되지 않았습니다.")

    return base_url


def _build_upload_endpoint() -> str:
    """
    오디오 업로드용 endpoint 생성

    현재 팀원 서버:
    POST /transcribe
    """

    return f"{_get_base_url()}/transcribe"


def _build_result_endpoint(task_id: str) -> str:
    """
    STT 결과 조회용 endpoint 생성

    현재 팀원 서버:
    GET /result/{task_id}
    """

    return f"{_get_base_url()}/result/{task_id}"


def _validate_wav_file(file_path: str) -> None:
    """
    STT 서버로 보내기 전에 wav 파일인지 확인한다.

    audio_service.py에서 이미 wav 변환을 했어야 하므로,
    여기서는 실수로 m4a, mp4, aac 등이 넘어오는 것을 방지한다.

    Parameters
    ----------
    file_path : str
        STT 서버에 보낼 오디오 파일 경로

    Raises
    ------
    FileNotFoundError
        파일이 존재하지 않을 때 발생

    ValueError
        wav 파일이 아닌 경우 발생
    """

    path = Path(file_path)

    if not path.exists():
        raise FileNotFoundError(f"오디오 파일을 찾을 수 없습니다: {file_path}")

    if not path.is_file():
        raise ValueError(f"오디오 파일 경로가 파일이 아닙니다: {file_path}")

    if path.suffix.lower() != ".wav":
        raise ValueError(
            "STT 서버에는 wav 파일만 전송해야 합니다. "
            f"현재 파일: {file_path}"
        )


def _extract_transcript_from_response(data: Any) -> str:
    """
    STT 서버 응답(JSON)에서 transcript를 최대한 유연하게 추출한다.

    지원 예시
    --------
    {"text": "..."}
    {"transcript": "..."}
    {"result": {"text": "..."}}
    {"result": {"transcript": "..."}}
    {"status": "done", "text": "..."}
    """

    if isinstance(data, dict):
        # 가장 흔한 응답 형태
        if isinstance(data.get("text"), str):
            return data["text"].strip()

        if isinstance(data.get("transcript"), str):
            return data["transcript"].strip()

        # 중첩된 result 구조 대응
        result = data.get("result")
        if isinstance(result, dict):
            if isinstance(result.get("text"), str):
                return result["text"].strip()

            if isinstance(result.get("transcript"), str):
                return result["transcript"].strip()

    raise RuntimeError(
        f"STT 응답에서 transcript를 찾지 못했습니다. 응답 데이터: {data}"
    )


def _upload_audio(file_path: str, timeout: int = 30) -> str:
    """
    STT 서버에 wav 오디오 파일을 업로드하고 task_id를 반환한다.

    Parameters
    ----------
    file_path : str
        업로드할 wav 오디오 파일 경로

    timeout : int
        업로드 요청 타임아웃(초)

    Returns
    -------
    str
        서버가 반환한 task_id
    """

    # STT 서버로 보내기 전 wav 파일인지 확인
    _validate_wav_file(file_path)

    endpoint = _build_upload_endpoint()
    filename = os.path.basename(file_path)

    # mimetypes로 한 번 추론하되, wav는 audio/wav로 고정
    guessed_mime_type, _ = mimetypes.guess_type(file_path)
    mime_type = guessed_mime_type or "audio/wav"

    # wav 파일은 명확하게 audio/wav로 보내는 것이 안전함
    if mime_type in ("audio/x-wav", "audio/vnd.wave"):
        mime_type = "audio/wav"

    with open(file_path, "rb") as audio_file:
        files = {
            # 팀원 서버 코드에서 request.files["file"] 로 받으므로 key는 "file" 이어야 함
            "file": (filename, audio_file, mime_type),
        }

        response = requests.post(
            endpoint,
            files=files,
            timeout=timeout,
        )

    # 디버깅용 로그
    print("STT upload endpoint =", endpoint)
    print("STT upload file =", filename)
    print("STT upload mime_type =", mime_type)
    print("STT upload status_code =", response.status_code)
    print("STT upload response_text =", response.text)

    response.raise_for_status()

    try:
        data = response.json()
    except ValueError as exc:
        raise RuntimeError(
            f"STT 업로드 응답이 JSON 형식이 아닙니다. 응답 본문: {response.text}"
        ) from exc

    print("STT upload response json =", data)

    task_id = data.get("task_id")
    if not isinstance(task_id, str) or not task_id.strip():
        raise RuntimeError(
            f"STT 업로드 응답에서 task_id를 찾지 못했습니다. 응답 데이터: {data}"
        )

    return task_id.strip()


def _poll_result(
    task_id: str,
    result_timeout: int = 180,
    poll_interval: float = 2.0,
) -> str:
    """
    task_id로 STT 결과를 반복 조회하여 최종 transcript를 반환한다.

    Parameters
    ----------
    task_id : str
        업로드 후 받은 작업 ID

    result_timeout : int
        전체 결과 대기 제한 시간(초)

    poll_interval : float
        조회 간격(초)

    Returns
    -------
    str
        최종 STT 결과 텍스트
    """

    endpoint = _build_result_endpoint(task_id)
    start_time = time.time()

    while True:
        elapsed = time.time() - start_time
        if elapsed > result_timeout:
            raise TimeoutError(
                f"STT 결과 대기 시간이 초과되었습니다. "
                f"task_id={task_id}, timeout={result_timeout}초"
            )

        response = requests.get(
            endpoint,
            timeout=10,
        )

        # 디버깅용 로그
        print("STT result endpoint =", endpoint)
        print("STT result status_code =", response.status_code)
        print("STT result response_text =", response.text)

        response.raise_for_status()

        try:
            data = response.json()
        except ValueError as exc:
            raise RuntimeError(
                f"STT 결과 응답이 JSON 형식이 아닙니다. 응답 본문: {response.text}"
            ) from exc

        print("STT result response json =", data)

        # 1) 바로 text가 있으면 완료
        if isinstance(data, dict) and isinstance(data.get("text"), str):
            return data["text"].strip()

        # 2) 서버가 done 상태와 함께 text를 줄 수도 있음
        if isinstance(data, dict) and data.get("status") == "done":
            return _extract_transcript_from_response(data)

        # 3) 처리 중이면 잠시 기다렸다가 재조회
        if isinstance(data, dict) and data.get("status") == "processing":
            time.sleep(poll_interval)
            continue

        # 4) 서버 내부 오류 상태
        if isinstance(data, dict) and data.get("status") == "error":
            message = data.get("message", "STT 서버 처리 중 오류가 발생했습니다.")
            raise RuntimeError(f"STT 서버 오류: {message}")

        # 5) 예상하지 못한 응답 형식
        raise RuntimeError(
            f"예상하지 못한 STT 결과 응답 형식입니다. 응답 데이터: {data}"
        )


def request_stt(
    file_path: str,
    upload_timeout: int = 30,
    result_timeout: int = 180,
    poll_interval: float = 2.0,
) -> str:
    """
    외부 STT 서버에 wav 오디오 파일을 전송하고 최종 transcript를 반환한다.

    동작 순서
    --------
    1. POST /transcribe 로 wav 파일 업로드
    2. task_id 수신
    3. GET /result/{task_id} 를 반복 조회
    4. 완료되면 transcript 반환

    Parameters
    ----------
    file_path : str
        전송할 wav 오디오 파일 경로

    upload_timeout : int
        업로드 요청 타임아웃(초)

    result_timeout : int
        결과 대기 최대 시간(초)

    poll_interval : float
        결과 조회 간격(초)

    Returns
    -------
    str
        최종 STT 결과 텍스트
    """

    task_id = _upload_audio(
        file_path=file_path,
        timeout=upload_timeout,
    )

    print("STT task_id =", task_id)

    return _poll_result(
        task_id=task_id,
        result_timeout=result_timeout,
        poll_interval=poll_interval,
    )


def transcribe_audio_file(
    file_path: str,
    upload_timeout: int = 30,
    result_timeout: int = 180,
    poll_interval: float = 2.0,
) -> str:
    """
    audio_service.py에서 호출하기 위한 함수.

    기존 audio_service.py가 transcribe_audio_file()을 import하고 있으므로,
    함수 이름을 유지해서 서비스 계층 코드를 크게 바꾸지 않도록 한다.

    Parameters
    ----------
    file_path : str
        STT 서버로 보낼 wav 파일 경로

    Returns
    -------
    str
        최종 STT 결과 텍스트
    """

    return request_stt(
        file_path=file_path,
        upload_timeout=upload_timeout,
        result_timeout=result_timeout,
        poll_interval=poll_interval,
    )