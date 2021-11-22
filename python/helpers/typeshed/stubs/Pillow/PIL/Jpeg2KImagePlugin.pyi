from typing import Any

from .ImageFile import ImageFile

class Jpeg2KImageFile(ImageFile):
    format: str
    format_description: str
    reduce: Any
    tile: Any
    def load(self): ...
