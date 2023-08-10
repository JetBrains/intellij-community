from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

MAGIC: bytes
FORMAT_DXT1: int
FORMAT_UNCOMPRESSED: int

class FtexImageFile(ImageFile):
    format: ClassVar[Literal["FTEX"]]
    format_description: ClassVar[str]
    def load_seek(self, pos) -> None: ...
