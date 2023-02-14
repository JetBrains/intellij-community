from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

class BitStream:
    fp: Any
    bits: int
    bitbuffer: int
    def __init__(self, fp) -> None: ...
    def next(self): ...
    def peek(self, bits): ...
    def skip(self, bits) -> None: ...
    def read(self, bits): ...

class MpegImageFile(ImageFile):
    format: ClassVar[Literal["MPEG"]]
    format_description: ClassVar[str]
