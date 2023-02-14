from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

class Jpeg2KImageFile(ImageFile):
    format: ClassVar[Literal["JPEG2000"]]
    format_description: ClassVar[str]
    reduce: Any
    tile: Any
    def load(self): ...
