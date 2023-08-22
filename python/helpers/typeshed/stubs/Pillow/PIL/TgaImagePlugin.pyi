from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

MODES: Any

class TgaImageFile(ImageFile):
    format: ClassVar[Literal["TGA"]]
    format_description: ClassVar[str]

SAVE: Any
