"""
pdf_converter.py

PDF 파일을 페이지별 이미지로 변환하는 유틸

역할
- 업로드된 PDF를 PNG 이미지 여러 장으로 변환
- 변환된 이미지 경로 목록 반환
- 기존 image OCR 로직이 이미지 경로를 처리할 수 있게 연결
"""

from __future__ import annotations

from pathlib import Path

from pdf2image import convert_from_path


def convert_pdf_to_images(
    pdf_path: str,
    output_dir: str,
    dpi: int = 200,
) -> list[str]:
    """
    PDF 파일을 페이지별 PNG 이미지로 변환한다.
    """

    pdf_file = Path(pdf_path)

    if not pdf_file.exists():
        raise FileNotFoundError(f"PDF 파일을 찾을 수 없습니다: {pdf_path}")

    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    pages = convert_from_path(
        pdf_path,
        dpi=dpi,
    )

    image_paths: list[str] = []

    for index, page in enumerate(pages, start=1):
        image_path = output_path / f"{pdf_file.stem}_page_{index}.png"
        page.save(image_path, "PNG")
        image_paths.append(str(image_path))

    return image_paths