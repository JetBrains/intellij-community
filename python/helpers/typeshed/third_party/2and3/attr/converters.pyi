from typing import TypeVar, Optional
from . import _ConverterType

_T = TypeVar('_T')

def optional(converter: _ConverterType[_T]) -> _ConverterType[Optional[_T]]: ...
