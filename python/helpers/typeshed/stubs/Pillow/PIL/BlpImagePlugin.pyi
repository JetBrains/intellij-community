from _typeshed import Incomplete
from enum import IntEnum
from typing import ClassVar, Literal

from .ImageFile import ImageFile, PyDecoder, PyEncoder

class Format(IntEnum):
    JPEG = 0

class Encoding(IntEnum):
    UNCOMPRESSED = 1
    DXT = 2
    UNCOMPRESSED_RAW_BGRA = 3

class AlphaEncoding(IntEnum):
    DXT1 = 0
    DXT3 = 1
    DXT5 = 7

def unpack_565(i): ...
def decode_dxt1(data, alpha: bool = False): ...
def decode_dxt3(data): ...
def decode_dxt5(data): ...

class BLPFormatError(NotImplementedError): ...

class BlpImageFile(ImageFile):
    format: ClassVar[Literal["BLP"]]
    format_description: ClassVar[str]

class _BLPBaseDecoder(PyDecoder):
    magic: Incomplete
    def decode(self, buffer): ...

class BLP1Decoder(_BLPBaseDecoder): ...
class BLP2Decoder(_BLPBaseDecoder): ...

class BLPEncoder(PyEncoder):
    def encode(self, bufsize): ...
