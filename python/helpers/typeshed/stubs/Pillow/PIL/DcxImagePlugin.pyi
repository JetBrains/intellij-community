from typing import Any, ClassVar
from typing_extensions import Literal

from .PcxImagePlugin import PcxImageFile

MAGIC: int

class DcxImageFile(PcxImageFile):
    format: ClassVar[Literal["DCX"]]
    frame: Any
    fp: Any
    def seek(self, frame) -> None: ...
    def tell(self): ...
