from typing import Any

from .TiffImagePlugin import TiffImageFile

class MicImageFile(TiffImageFile):
    format: str
    format_description: str
    fp: Any
    frame: Any
    def seek(self, frame) -> None: ...
    def tell(self): ...
