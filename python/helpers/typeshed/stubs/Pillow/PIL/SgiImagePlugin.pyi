from typing import Any

from .ImageFile import ImageFile, PyDecoder

MODES: Any

class SgiImageFile(ImageFile):
    format: str
    format_description: str

class SGI16Decoder(PyDecoder):
    def decode(self, buffer): ...
