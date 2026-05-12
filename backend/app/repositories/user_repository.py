"""
user_repository.py

사용자(User) DB 접근 계층

역할
- username으로 사용자 조회
- 회원가입 시 새 사용자 DB 저장

주의
- repository는 DB 작업만 담당한다.
- 비밀번호 해시 처리는 service 계층에서 처리한다.
- 로그인은 email이 아니라 username 기준으로 처리한다.
"""

from sqlalchemy.orm import Session

from models.user_model import User


def get_user_by_username(db: Session, username: str) -> User | None:
    """
    username으로 사용자 조회

    사용 위치
    -------
    1. 회원가입 시 username 중복 검사
    2. 로그인 시 username으로 사용자 찾기

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    username : str
        로그인에 사용할 사용자 ID

    Returns
    -------
    User | None
        사용자가 존재하면 User 객체 반환
        없으면 None 반환
    """

    return (
        db.query(User)
        .filter(User.username == username)
        .first()
    )


def create_user(
    db: Session,
    username: str,
    hashed_password: str,
) -> User:
    """
    새 사용자 DB 저장

    처리 과정
    -------
    1. service 계층에서 전달받은 username, hashed_password로 User 객체 생성
    2. DB에 저장
    3. 저장된 User 객체 반환

    Parameters
    ----------
    db : Session
        SQLAlchemy DB 세션

    username : str
        로그인에 사용할 사용자 ID

    hashed_password : str
        service 계층에서 이미 해시 처리된 비밀번호

    Returns
    -------
    User
        DB에 저장된 사용자 객체
    """

    # User ORM 객체 생성
    # 주의: 여기서는 비밀번호를 해시하지 않는다.
    # 해시 처리는 auth_service.py에서 담당한다.
    user = User(
        username=username,
        hashed_password=hashed_password,
    )

    # DB에 사용자 추가
    db.add(user)

    # 변경 사항 저장
    db.commit()

    # DB에서 생성된 id, created_at 등의 값을 다시 반영
    db.refresh(user)

    return user