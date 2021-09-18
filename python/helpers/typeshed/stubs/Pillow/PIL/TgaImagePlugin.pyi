from typing import Any

from .ImageFile import ImageFile

MODES: Any

class TgaImageFile(ImageFile):
    format: str
    format_description: str

SAVE: Any
