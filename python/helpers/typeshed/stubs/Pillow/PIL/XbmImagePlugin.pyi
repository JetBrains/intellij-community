from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

xbm_head: Any

class XbmImageFile(ImageFile):
    format: ClassVar[Literal["XBM"]]
    format_description: ClassVar[str]
