from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

class SunImageFile(ImageFile):
    format: ClassVar[Literal["SUN"]]
    format_description: ClassVar[str]
