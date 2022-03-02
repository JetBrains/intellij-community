from typing import Any, ClassVar
from typing_extensions import Literal

from .TiffImagePlugin import TiffImageFile

class MicImageFile(TiffImageFile):
    format: ClassVar[Literal["MIC"]]
    fp: Any
    frame: Any
    def seek(self, frame) -> None: ...
    def tell(self): ...
