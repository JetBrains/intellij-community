from typing import Any

from .ImageFile import ImageFile

MODES: Any

class PsdImageFile(ImageFile):
    format: str
    format_description: str
    mode: Any
    tile: Any
    frame: Any
    fp: Any
    def seek(self, layer): ...
    def tell(self): ...
    im: Any
    def load_prepare(self) -> None: ...
