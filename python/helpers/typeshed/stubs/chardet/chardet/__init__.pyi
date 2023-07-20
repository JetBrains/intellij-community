import sys

from .universaldetector import UniversalDetector as UniversalDetector, _FinalResultType, _IntermediateResultType
from .version import VERSION as VERSION, __version__ as __version__

if sys.version_info >= (3, 8):
    from typing import TypedDict
else:
    from typing_extensions import TypedDict

# unused in this module, but imported in multiple submodules
class _LangModelType(TypedDict):  # noqa: Y049
    char_to_order_map: tuple[int, ...]
    precedence_matrix: tuple[int, ...]
    typical_positive_ratio: float
    keep_english_letter: bool
    charset_name: str
    language: str

def detect(byte_str: bytes | bytearray) -> _FinalResultType: ...
def detect_all(byte_str: bytes | bytearray, ignore_threshold: bool = ...) -> list[_IntermediateResultType]: ...
