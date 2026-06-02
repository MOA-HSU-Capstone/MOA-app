

# MOA

<div align="center">
  <img src="https://github.com/user-attachments/assets/4aff31ff-26c8-4ff2-8aed-af6f00282c58" width="100%" alt="MOA 표지" />
</div>

MOA(Multimodal Orchestrated Assistant **(모아)** — 회의 음성·문서·이미지를 모아 AI가 요약·결정 사항·할 일까지 정리해 주는 모바일 회의 관리 서비스입니다.

  


> 회의를 기록하는 시간을 줄이고, 회의의 가치를 높이다.

  


**한성대학교 모바일소프트웨어트랙 캡스톤디자인 · 연대기팀** · 개발 기간 2026.03.05 ~ 2026.06.05



---

## 기술 스택

배지를 클릭하면 해당 기술의 공식 문서 또는 제품 페이지로 이동합니다.

### Android 클라이언트 (`:app` 모듈)

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[Kotlin](https://kotlinlang.org)
[Jetpack Navigation](https://developer.android.com/guide/navigation)
[Lifecycle](https://developer.android.com/topic/libraries/architecture/lifecycle)
[WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started)
[Retrofit](https://square.github.io/retrofit/)
[OkHttp](https://square.github.io/okhttp/)
[Gson](https://github.com/google/gson)
[Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
[ML Kit Doc Scanner](https://developers.google.com/ml-kit/vision/doc-scanner)
[Material](https://m3.material.io/)
[Gradle](https://gradle.org/)

### 백엔드·AI (시스템 구성)

[Python](https://www.python.org/)
[FastAPI](https://fastapi.tiangolo.com/)
[Uvicorn](https://www.uvicorn.org/)
[SQLite](https://www.sqlite.org/)
[JWT](https://jwt.io/)
[OpenAI](https://platform.openai.com/)
[Google Cloud](https://cloud.google.com/speech-to-text)

---

## 프로젝트 디렉토리

저장소 루트 기준 주요 구조입니다.

```text
MOA/
├── app/                          # Android 애플리케이션 (단일 Gradle 모듈)
│   └── src/main/
│       ├── java/.../a20260310/
│       │   ├── MainActivity.kt   # NavHost + 드로어 네비게이션
│       │   ├── data/             # remote(API)·local·repository·model·auth·poll
│       │   ├── ui/               # splash, login, home, add, recording, summary, detail, history, addcomplete
│       │   ├── viewmodel/
│       │   └── worker/           # 요약 완료 폴링 등 WorkManager
│       └── res/
│           ├── navigation/       # nav_graph.xml
│           ├── layout/, menu/, values/
│           └── ...
├── backend/                      # FastAPI API 서버 (Python)
│   └── app/
│       ├── main.py
│       ├── routers/, services/, repositories/, models/
│       ├── ai/                   # STT 연동, 요약, OCR 등
│       ├── config/, storage/, utils/
│       └── ...
├── assets/                       # README용 이미지 (시스템 아키텍처 등)
├── api_server.py                 # 보조 STT 실험용 Flask 스크립트(선택)
├── gradle/
│   └── libs.versions.toml        # 버전 카탈로그
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew, gradlew.bat
└── README.md
```

---

## 주요 기능


| 구분    | 내용                                              |
| ----- | ----------------------------------------------- |
| 회의·폴더 | 폴더 단위로 회의 구분, 목록·상세에서 일정·상태 확인                  |
| 입력 소스 | 문서(PDF·이미지)·오디오 업로드, 기기 녹음, ML Kit 문서 스캔        |
| AI·요약 | 서버 STT·문서·이미지 처리 후 요약·결정 사항·할 일(Action Item) 생성 |
| 요약 UX | 진행률·예상 시간, 요약 대기 큐, WorkManager 백그라운드 폴링        |
| 회의 상세 | 요약·결정·할 일 편집, 첨부 파일 탭·다운로드                      |
| 인증    | JWT 기반 로그인·회원가입                                 |


---

## 시스템 아키텍처

팀에서 정의한 전체 시스템 구성입니다. Android 클라이언트는 **REST API + JWT**로 백엔드와 통신하고, 백엔드는 **SQLite**와 로컬 파일 저장소, **Google Cloud STT**·**OpenAI** 등과 연동합니다.

다음 이미지는 저장소 [`assets/architecture.png`](assets/architecture.png) 파일입니다.

<div align="center">
  <img src="./assets/architecture.png" width="100%" alt="MOA 시스템 아키텍처" />
</div>

사용자는 Android 앱에서 회의 자료를 올리고, API 서버가 메타데이터와 파일을 저장한 뒤 AI 서비스로 분석·요약 결과를 받아 다시 앱에 전달하는 흐름으로 동작합니다.

---

## 앱 화면 네비게이션

[`nav_graph.xml`](app/src/main/res/navigation/nav_graph.xml) · [`MainActivity`](app/src/main/java/com/example/a20260310/MainActivity.kt) 기준으로, **자주 쓰는 화면만** 정리했습니다.

| 화면 | 역할 |
| --- | --- |
| `HomeFragment` | 메인: 회의 목록, 요약 패널, 회의 추가·상세로 진입 |
| `DetailFragment` | 회의 상세(요약·결정·할 일·첨부 파일) |
| `AddMethodFragment` → `SummarizingFragment` → `SummaryFragment` | 자료 등록 후 요약 진행·결과 확인(필요 시 `RecordingFragment`에서 녹음) |
| `LoginFragment` / `SignupFragment` | 로그인·회원가입 |
| `HistoryFragment` | 드로어 **설정**, 로그아웃 |

회의 추가 전체 순서: **홈** → 폴더 선택 → 회의 정보 작성 → 자료 추가 → 요약 중 → 요약 결과 → 완료 후 **홈** 복귀.

## 팀 (연대기)


| 이름  | 역할                                  |
| --- | ----------------------------------- |
| 김민서 | Android Frontend / UI · UX          |
| 오형채 | Backend (API 연동 서버 구축 및 DB 아키텍처 설계) |
| 박민혁 | STT (API 연동 및 서버 인프라 관리)            |
| 신현규 | LLM (AI 모델 최적화 및 UI 인터페이스 설계)       |


---

한성대학교 모바일소프트웨어트랙 캡스톤디자인 프로젝트입니다.
