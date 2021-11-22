from typing import Any

from .PcxImagePlugin import PcxImageFile

MAGIC: int

class DcxImageFile(PcxImageFile):
    format: str
    format_description: str
    frame: Any
    fp: Any
    def seek(self, frame) -> None: ...
    def tell(self): ...
