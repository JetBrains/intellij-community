from typing import Any

from .ImageFile import ImageFile

class GbrImageFile(ImageFile):
    format: str
    format_description: str
    im: Any
    def load(self) -> None: ...
