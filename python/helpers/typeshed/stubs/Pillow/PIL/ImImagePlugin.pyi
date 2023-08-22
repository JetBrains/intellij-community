from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

COMMENT: str
DATE: str
EQUIPMENT: str
FRAMES: str
LUT: str
NAME: str
SCALE: str
SIZE: str
MODE: str
TAGS: Any
OPEN: Any
split: Any

def number(s): ...

class ImImageFile(ImageFile):
    format: ClassVar[Literal["IM"]]
    format_description: ClassVar[str]
    @property
    def n_frames(self): ...
    @property
    def is_animated(self): ...
    frame: Any
    fp: Any
    tile: Any
    def seek(self, frame) -> None: ...
    def tell(self): ...

SAVE: Any
