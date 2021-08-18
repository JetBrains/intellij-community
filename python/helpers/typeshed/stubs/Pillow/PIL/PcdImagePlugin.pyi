from typing import Any

from .ImageFile import ImageFile

class PcdImageFile(ImageFile):
    format: str
    format_description: str
    im: Any
    def load_end(self) -> None: ...
