from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

xbm_head: Incomplete

class XbmImageFile(ImageFile):
    format: ClassVar[Literal["XBM"]]
    format_description: ClassVar[str]
