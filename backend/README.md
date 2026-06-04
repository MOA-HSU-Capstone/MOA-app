# MOA Backend (Python)

이 폴더는 **MOA 프로젝트의 Python 백엔드 위치**입니다.  
현재는 서버 프레임워크(FastAPI/Flask 등)가 미정이므로, **프레임워크에 종속되지 않는 AI 파이프라인 코드**만 제공합니다.

## 현재 구현 상태

- **실제 구현된 파일(바로 사용 가능)**
  - `utils/preprocess.py`: STT 최소 전처리(공백 정리/도메인 용어 치환/라인 번호+마침표)
  - `services/llm_service.py`: OpenAI API 기반 회의 분석(JSON 출력)
  - `services/pipeline.py`: 전처리 + LLM 분석 orchestration
- **placeholder(TODO만 존재)**
  - Whisper STT 연동(`services/stt_service.py`)
  - OCR 연동(`services/ocr_service.py`)
  - 설정/환경변수 관리(`core/config.py`)
  - DB 저장/조회(`db/database.py`)
  - 스키마 모델(`models/meeting.py`)
  - 테스트(`tests/test_pipeline.py`)

## 환경 변수

`.env.example` 참고:

- `OPENAI_API_KEY`
- `OPENAI_MODEL` (기본값: `gpt-4o-mini`)

## 설치

```bash
cd backend
python -m venv .venv
# Windows PowerShell
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

## 실행 예시 (프레임워크 없이)

PowerShell 기준:

```bash
cd backend
$env:OPENAI_API_KEY="YOUR_KEY"
$env:OPENAI_MODEL="gpt-4o-mini"
python -c "from services.pipeline import process_meeting_text; import json; print(json.dumps(process_meeting_text('오쓰 로그인 관련해서 디비 인덱수 문제도 같이 봅시다', ocr_text='화이트보드: API rate limit', title='주간 회의'), ensure_ascii=False, indent=2))"
```

## 향후 계획

- STT: Google Cloud STT 대신 **Whisper 기반**으로 `services/stt_service.py`에 구현 예정
- OCR: 필요 시 엔진 연동 또는 전처리 로직 추가 예정
- DB: 결과 저장/조회 계층 분리 후 연동 예정
- API 서버: FastAPI/Flask/Spring 등 어떤 형태로든 `services.pipeline.process_meeting_text`를 import 해서 연결 가능하도록 유지

