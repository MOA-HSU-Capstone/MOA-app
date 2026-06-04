"""
base.py

SQLAlchemy ORM 모델의 공통 Base 정의 파일

역할
- 모든 ORM 모델이 공통으로 상속받는 Base 제공
- 메타데이터 관리의 기준 제공
- 테이블 생성 시 Base.metadata를 통해 전체 모델을 인식

주의
- engine / session 생성은 config/database.py에서 담당
- Base는 models 폴더에서 별도로 관리
"""

from sqlalchemy.orm import declarative_base


# 모든 ORM 모델이 상속받는 공통 Base
Base = declarative_base()