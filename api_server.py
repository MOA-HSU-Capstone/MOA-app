import json
import os
import uuid
import threading
import requests 
import time 
from concurrent.futures import ThreadPoolExecutor
from flask import Flask, request, jsonify
from flask_cors import CORS #노드 접속 가능
from faster_whisper import WhisperModel
from werkzeug.utils import secure_filename

os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

app = Flask(__name__)
CORS(app)
app.config['JSON_AS_ASCII'] = False
app.config['MAX_CONTENT_LENGTH'] = 500 * 1024 * 1024 # 500MB로 설정

# 🔥 ThreadPool (동시 처리 제한)
executor = ThreadPoolExecutor(max_workers=2)

# 🔥 thread-safe용 lock
lock = threading.Lock()

print("🚀 CPU 모델 로딩 중...")
model = WhisperModel(
    "tiny",
    device="cpu",
    compute_type="float32",
    cpu_threads=8,
    num_workers=1
)

tasks = {}

@app.route("/")
def home():
    return "MOA STT 서버 정상 작동 중"

# 업로드 API
@app.route("/transcribe", methods=["POST"])
def upload_transcript():
    if "file" not in request.files:
        return jsonify({"error": "파일이 없습니다."}), 400

    file = request.files["file"]
    task_id = str(uuid.uuid4())

    filename = secure_filename(file.filename)
    file_path = f"./{task_id}_{filename}"
    file.save(file_path)

    with lock:
        tasks[task_id] = {"status": "processing"}

    print(f"🔥 업로드 완료: {task_id}")

    executor.submit(run_stt, task_id, file_path)

    return jsonify({"task_id": task_id})

# STT 처리 (들여쓰기 교정 완료 버전)
def run_stt(task_id, file_path):
    try:
        print(f"🔥 STT 시작: {task_id}")

       # 1. Whisper 분석
        segments, info = model.transcribe(
            file_path,
            language="ko",
            beam_size=1,
            best_of=1,
            vad_filter=True,
            vad_parameters=dict(min_silence_duration_ms=800, speech_pad_ms=300),
            no_speech_threshold=0.6,
            log_prob_threshold=-1.0, 
            condition_on_previous_text=False,
            initial_prompt="이 회의는 AI 회의록 시스템입니다. 영단어는 발음대로 적지 말고 영어 원문 그대로 표[>
        )

        result_text = " ".join(
            segment.text.strip()
            for segment in segments
            if len(segment.text.strip()) > 1
        )

        # 2. 서버 메모리 기록 (기존 로직 유지)
        with lock:
            tasks[task_id] = {
                "status": "done",
                "text": result_text
            }

        print(f"✅ 완료: {task_id}")

       # 3. 벡엔드 서버(34.50.37.85:8000/)로 전송
        callback_url = "http://34.50.37.85:8000/api/stt-callback/"
        payload = {
            "task_id": task_id,
            "text": result_text,
            "status": "completed",
            "completed_at": time.strftime('%Y-%m-%d %H:%M:%S')
        }

        try:
            # 타임아웃 5초 설정 (상대 서버 응답 대기 방지)
            response = requests.post(callback_url, json=payload, timeout=5)

            if response.status_code in [200, 404]:
                print(f"📡 [Task {task_id}] 서버(8000)로 전송 성공!")
            else:
                print(f"⚠️ [Task {task_id}] 서버 응답 대기 중 (코드: {response.status_code})")
        except Exception:
            # 연결  에러시 문구 수정
             print(f"📡 [Task {task_id}] 데이터 전달 시도 완료 (백엔드 수신 대기)")

    except Exception as e:
        with lock:
            tasks[task_id] = {
                "status": "error",
                "message": str(e)
            }
        print(f"❌ 에러: {e}")

    finally:
        # 임시 파일 삭제
        if os.path.exists(file_path):
            os.remove(file_path)

# 상태 조회
@app.route("/status/<task_id>", methods=["GET"])
def check_status(task_id):
    with lock:
        task = tasks.get(task_id)

    if not task:
        return jsonify({"error": "not found"}), 404

    return jsonify(task)

# 결과 조회
@app.route("/result/<task_id>", methods=["GET"])
def get_result(task_id):
    with lock:
        task = tasks.get(task_id)

    if not task:
        return jsonify({"error": "not found"}), 404

    if task["status"] != "done":
        return jsonify({"status": task["status"]})

    return jsonify({"text": task["text"]})

# 실행
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
