from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile

class GdImageFile(ImageFile):
    format: ClassVar[Literal["GD"]]
    format_description: ClassVar[str]

def open(fp, mode: str = ...): ...
