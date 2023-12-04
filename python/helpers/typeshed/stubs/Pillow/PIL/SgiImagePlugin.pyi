from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile, PyDecoder

MODES: Incomplete

class SgiImageFile(ImageFile):
    format: ClassVar[Literal["SGI"]]
    format_description: ClassVar[str]

class SGI16Decoder(PyDecoder):
    def decode(self, buffer): ...
