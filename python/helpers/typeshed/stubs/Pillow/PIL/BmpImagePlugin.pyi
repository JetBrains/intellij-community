from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

BIT2MODE: Any

class BmpImageFile(ImageFile):
    format_description: ClassVar[str]
    format: ClassVar[Literal["BMP", "DIB", "CUR"]]
    COMPRESSIONS: Any

class DibImageFile(BmpImageFile):
    format: ClassVar[Literal["DIB"]]

SAVE: Any
