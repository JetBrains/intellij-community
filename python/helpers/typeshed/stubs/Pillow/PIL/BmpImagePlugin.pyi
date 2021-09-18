from typing import Any

from .ImageFile import ImageFile

BIT2MODE: Any

class BmpImageFile(ImageFile):
    format_description: str
    format: str
    COMPRESSIONS: Any

class DibImageFile(BmpImageFile):
    format: str
    format_description: str

SAVE: Any
