from typing import TypeVar

_T = TypeVar("_T")

class BaseClass: ...

def encode_obj(in_obj: _T) -> _T: ...
