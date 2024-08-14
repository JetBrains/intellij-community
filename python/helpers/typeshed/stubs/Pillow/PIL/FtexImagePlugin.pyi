from enum import IntEnum
from typing import ClassVar, Literal

from .ImageFile import ImageFile

MAGIC: bytes

class Format(IntEnum):
    DXT1 = 0
    UNCOMPRESSED = 1

class FtexImageFile(ImageFile):
    format: ClassVar[Literal["FTEX"]]
    format_description: ClassVar[str]
    def load_seek(self, pos) -> None: ...
