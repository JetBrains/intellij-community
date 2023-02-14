from typing import ClassVar
from typing_extensions import Literal

from .ImageFile import ImageFile, PyDecoder

class MspImageFile(ImageFile):
    format: ClassVar[Literal["MSP"]]
    format_description: ClassVar[str]

class MspDecoder(PyDecoder):
    def decode(self, buffer): ...
