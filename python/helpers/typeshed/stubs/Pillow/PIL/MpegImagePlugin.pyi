from typing import Any

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
    format: str
    format_description: str
