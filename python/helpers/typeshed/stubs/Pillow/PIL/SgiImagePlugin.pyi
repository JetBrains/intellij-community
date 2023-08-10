from typing import Any, ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile, PyDecoder

MODES: Any

class SgiImageFile(ImageFile):
    format: ClassVar[Literal["SGI"]]
    format_description: ClassVar[str]

class SGI16Decoder(PyDecoder):
    def decode(self, buffer): ...
