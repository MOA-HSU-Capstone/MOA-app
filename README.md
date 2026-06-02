# MOA (Meeting Organizer Assistant)

한성대학교 모바일소프트웨어트랙 캡스톤디자인 **연대기팀** 프로젝트

<img width="1061" alt="MOA" src="https://github.com/user-attachments/assets/075a8ff8-c62d-4dd8-80c0-d2ff33d5018d" />

<div align="center">
  <iframe src="https://htmlpreview.github.io/?https://github.com/AllforMinseo/MOA/blob/main/moa_card.html" width="820" height="620" frameborder="0" scrolling="no"></iframe>
</div>
---

# 📌  작품 개요

- 작품명 : MOA
- 개발 기간 : 2026.03.05 ~ 2026.06.05
- 팀명 : 연대기

# 📌 프로젝트 소개

MOA(Meeting Organizer Assistant, 모아)는 회의 음성 파일, 문서, 이미지 자료를 종합해 AI가 자동으로 회의 내용을 분석하고 정리해주는 스마트 회의 관리 서비스입니다.

기존의 회의록 생성 서비스들은 음성만을 기반으로 합니다. MOA는 이러한 기존 서비스들의 한계를 넘어 회의 중에 사용한 문서 자료와 이미지 자료들도 첨부해 회의록을 자동으로 생성할 수 있습니다.

사용자는 모바일 환경에서 회의 자료를 업로드하고, AI가 생성한 회의 요약과 핵심 내용을 확인하며 체계적으로 관리할 수 있습니다.

---

# 🎯 개발 목적

회의가 끝난 후 회의록을 정리하는 과정은 많은 시간이 소요되며 중요한 내용이 누락될 가능성이 있습니다.

MOA는 다음과 같은 문제를 해결하고자 합니다.

* 회의록 작성 시간 단축
* 회의 내용 자동 요약
* 결정 사항 자동 추출
* Action Item 자동 생성
* 회의 자료 통합 관리
* 모바일 기반 접근성 향상

---

# ✨ 주요 기능

## 📂 회의록 관리

* 회의별 폴더 생성
* 회의록 저장 및 조회
* 폴더별 회의록 분류

## 🎤 음성 파일 분석

* STT(Speech To Text) 기반 음성 변환
* 회의 발언 내용 추출
* 화자별 내용 분석

## 📄 문서 및 이미지 분석

* OCR 기반 텍스트 추출
* 회의 자료 내용 분석
* 이미지 내 텍스트 인식

## 🤖 AI 회의 분석

OpenAI GPT 기반 분석 기능 제공

* 회의 요약(Summary)
* 핵심 내용(Key Points)
* 결정 사항(Decisions)
* Action Items 생성

## 📱 Android 앱

* 모바일 환경 지원
* 파일 업로드
* 회의록 조회
* AI 분석 결과 확인

---

# 🏗 시스템 아키텍처

```text
Android App
      │
      ▼
Spring Boot Server
      │
      ▼
File Storage
      │
      ▼
AI Processing Pipeline
 ├── OCR
 ├── STT
 └── GPT Analysis
      │
      ▼
MySQL Database
      │
      ▼
Meeting Summary
Decision
Action Items
```

---

# 🛠 기술 스택

## Frontend

* Kotlin
* Android Studio
* Material Design

## Backend

* Spring Boot
* Java
* REST API

## AI

* OpenAI GPT
* OCR
* STT

## Database

* MySQL

## Infrastructure

* GitHub
* Git
* Postman

---

# 📁 프로젝트 구조

```text
MOA
├── Android
│   ├── Login
│   ├── Folder
│   ├── Meeting
│   ├── Upload
│   └── AI Summary
│
├── Backend
│   ├── Controller
│   ├── Service
│   ├── Repository
│   ├── Entity
│   └── Config
│
├── AI
│   ├── OCR
│   ├── STT
│   └── GPT Analysis
│
└── Database
    └── MySQL
```

---

# 🚀 기대 효과

* 회의록 작성 시간 감소
* 업무 생산성 향상
* 회의 정보 관리 효율화
* 모바일 기반 회의 관리 지원
* AI 기반 의사결정 지원

---

# 👥 팀 소개

## 연대기팀

| 이름  | 역할                         |
| --- | -------------------------- |
| 김민서 | Android Frontend / UI · UX                     |
| 오형채 | Backend (API 연동 서버 구축 및 DB 아키텍처 설계) |
| 박민혁 | STT (API 연동 및 서버 인프라 관리)              |
| 신현규 | LLM (AI 모델 최적화 및 UI 인터페이스 설계)       |

---

# 📷 주요 화면

* 로그인
* 회원가입
* 폴더 관리
* 회의록 업로드
* AI 요약 결과
* 회의록 상세 조회

(프로젝트 화면 이미지 추가 예정)

---

# 🎓 Capstone Design Project

한성대학교 모바일소프트웨어트랙 캡스톤디자인 프로젝트

MOA는 AI 기반 회의록 자동 생성 및 관리 서비스를 목표로 개발되었습니다.

---

## 📌 한 줄 소개

> "회의를 기록하는 시간을 줄이고, 회의의 가치를 높이다."
>
> MOA (Meeting Organizer Assistant)
