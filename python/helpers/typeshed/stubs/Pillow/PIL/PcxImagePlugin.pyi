from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

logger: Any

class PcxImageFile(ImageFile):
    format: ClassVar[Literal["PCX", "DCX"]]
    format_description: ClassVar[str]

SAVE: Any
