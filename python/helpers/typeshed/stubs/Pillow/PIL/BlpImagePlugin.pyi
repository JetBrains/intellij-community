from typing import Any

from .ImageFile import ImageFile, PyDecoder

BLP_FORMAT_JPEG: int
BLP_ENCODING_UNCOMPRESSED: int
BLP_ENCODING_DXT: int
BLP_ENCODING_UNCOMPRESSED_RAW_BGRA: int
BLP_ALPHA_ENCODING_DXT1: int
BLP_ALPHA_ENCODING_DXT3: int
BLP_ALPHA_ENCODING_DXT5: int

def unpack_565(i): ...
def decode_dxt1(data, alpha: bool = ...): ...
def decode_dxt3(data): ...
def decode_dxt5(data): ...

class BLPFormatError(NotImplementedError): ...

class BlpImageFile(ImageFile):
    format: str
    format_description: str

class _BLPBaseDecoder(PyDecoder):
    magic: Any
    def decode(self, buffer): ...

class BLP1Decoder(_BLPBaseDecoder): ...
class BLP2Decoder(_BLPBaseDecoder): ...
