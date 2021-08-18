from typing import Any

from .ImageFile import ImageFile

logger: Any

class PcxImageFile(ImageFile):
    format: str
    format_description: str

SAVE: Any
