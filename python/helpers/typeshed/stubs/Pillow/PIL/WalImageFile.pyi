from typing import ClassVar
from typing_extensions import Literal

from . import ImageFile

class WalImageFile(ImageFile.ImageFile):
    format: ClassVar[Literal["WAL"]]
    format_description: ClassVar[str]
    def load(self) -> None: ...

def open(filename): ...

quake2palette: bytes
