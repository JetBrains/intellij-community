from typing import Any

from .ImageFile import ImageFile

b_whitespace: bytes
MODES: Any

class PpmImageFile(ImageFile):
    format: str
    format_description: str
