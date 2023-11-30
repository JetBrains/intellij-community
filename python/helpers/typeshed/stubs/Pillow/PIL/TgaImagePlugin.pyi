from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

MODES: Incomplete

class TgaImageFile(ImageFile):
    format: ClassVar[Literal["TGA"]]
    format_description: ClassVar[str]

SAVE: Incomplete
