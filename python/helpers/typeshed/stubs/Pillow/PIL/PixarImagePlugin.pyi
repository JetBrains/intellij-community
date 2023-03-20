from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

class PixarImageFile(ImageFile):
    format: ClassVar[Literal["PIXAR"]]
    format_description: ClassVar[str]
