from typing import ClassVar
from typing_extensions import Literal

from .BmpImagePlugin import BmpImageFile

class CurImageFile(BmpImageFile):
    format: ClassVar[Literal["CUR"]]
