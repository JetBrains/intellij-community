from typing import Any, ClassVar
from typing_extensions import Literal

from .JpegImagePlugin import JpegImageFile

class MpoImageFile(JpegImageFile):
    format: ClassVar[Literal["MPO"]]
    def load_seek(self, pos) -> None: ...
    fp: Any
    offset: Any
    tile: Any
    def seek(self, frame) -> None: ...
    def tell(self): ...
    @staticmethod
    def adopt(jpeg_instance, mpheader: Any | None = ...): ...
