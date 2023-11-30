from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal

from .PcxImagePlugin import PcxImageFile

MAGIC: int

class DcxImageFile(PcxImageFile):
    format: ClassVar[Literal["DCX"]]
    frame: Incomplete
    fp: Incomplete
    def seek(self, frame) -> None: ...
    def tell(self): ...
