# Stubs for hmac

from typing import Any, Callable, Optional, Union, overload
from types import ModuleType
import sys

_B = Union[bytes, bytearray]

# TODO more precise type for object of hashlib
_Hash = Any

if sys.version_info >= (3, 4):
    def new(key: _B, msg: Optional[_B] = ...,
            digestmod: Optional[Union[str, Callable[[], _Hash], ModuleType]] = ...) -> HMAC: ...
else:
    def new(key: _B, msg: Optional[_B] = ...,
            digestmod: Optional[Union[Callable[[], _Hash], ModuleType]] = ...) -> HMAC: ...

class HMAC:
    if sys.version_info >= (3,):
        digest_size = ...  # type: int
    if sys.version_info >= (3, 4):
        block_size = ...  # type: int
        name = ...  # type: str
    def update(self, msg: _B) -> None: ...
    def digest(self) -> bytes: ...
    def hexdigest(self) -> str: ...
    def copy(self) -> HMAC: ...

@overload
def compare_digest(a: str, b: str) -> bool: ...
@overload
def compare_digest(a: bytes, b: bytes) -> bool: ...
@overload
def compare_digest(a: bytearray, b: bytearray) -> bool: ...
