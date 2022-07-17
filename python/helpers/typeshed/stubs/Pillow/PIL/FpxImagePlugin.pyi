from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

MODES: Any

class FpxImageFile(ImageFile):
    format: ClassVar[Literal["FPX"]]
    format_description: ClassVar[str]
    fp: Any
    def load(self): ...
