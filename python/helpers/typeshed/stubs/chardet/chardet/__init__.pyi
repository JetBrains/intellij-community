import sys

from .universaldetector import UniversalDetector as UniversalDetector, _FinalResultType, _IntermediateResultType
from .version import VERSION as VERSION, __version__ as __version__

if sys.version_info >= (3, 8):
    from typing import TypedDict
else:
    from typing_extensions import TypedDict

class _LangModelType(TypedDict):
    char_to_order_map: tuple[int, ...]
    precedence_matrix: tuple[int, ...]
    typical_positive_ratio: float
    keep_english_letter: bool
    charset_name: str
    language: str

class _SMModelType(TypedDict):
    class_table: tuple[int, ...]
    class_factor: int
    state_table: tuple[int, ...]
    char_len_table: tuple[int, ...]
    name: str

def detect(byte_str: bytes | bytearray) -> _FinalResultType: ...
def detect_all(byte_str: bytes | bytearray, ignore_threshold: bool = ...) -> list[_IntermediateResultType]: ...
