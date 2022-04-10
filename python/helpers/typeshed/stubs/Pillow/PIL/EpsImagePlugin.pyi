from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

split: Any
field: Any
gs_windows_binary: Any

def has_ghostscript(): ...
def Ghostscript(tile, size, fp, scale: int = ..., transparency: bool = ...): ...

class PSFile:
    fp: Any
    char: Any
    def __init__(self, fp) -> None: ...
    def seek(self, offset, whence=...) -> None: ...
    def readline(self): ...

class EpsImageFile(ImageFile):
    format: ClassVar[Literal["EPS"]]
    format_description: ClassVar[str]
    mode_map: Any
    im: Any
    mode: Any
    tile: Any
    def load(self, scale: int = ..., transparency: bool = ...) -> None: ...
    def load_seek(self, *args, **kwargs) -> None: ...
