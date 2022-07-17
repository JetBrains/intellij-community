import sys
from typing import Any

from .universaldetector import UniversalDetector as UniversalDetector

def __getattr__(name: str) -> Any: ...  # incomplete

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
