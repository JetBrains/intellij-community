from typing import Any

from .ImageFile import ImageFile

SUPPORTED: bool

class WebPImageFile(ImageFile):
    format: str
    format_description: str
    def seek(self, frame) -> None: ...
    fp: Any
    tile: Any
    def load(self): ...
    def tell(self): ...
