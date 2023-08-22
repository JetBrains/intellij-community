from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

SUPPORTED: bool

class WebPImageFile(ImageFile):
    format: ClassVar[Literal["WEBP"]]
    format_description: ClassVar[str]
    def seek(self, frame) -> None: ...
    fp: Any
    tile: Any
    def load(self): ...
    def tell(self): ...
