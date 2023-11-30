from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal

from .JpegImagePlugin import JpegImageFile

class MpoImageFile(JpegImageFile):
    format: ClassVar[Literal["MPO"]]
    def load_seek(self, pos) -> None: ...
    fp: Incomplete
    offset: Incomplete
    tile: Incomplete
    def seek(self, frame) -> None: ...
    def tell(self): ...
    @staticmethod
    def adopt(jpeg_instance, mpheader: Incomplete | None = None): ...
