from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

class PcxImageFile(ImageFile):
    format: ClassVar[Literal["PCX", "DCX"]]
    format_description: ClassVar[str]

SAVE: Incomplete
