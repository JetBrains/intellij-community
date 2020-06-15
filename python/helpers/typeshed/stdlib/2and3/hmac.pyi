# Stubs for hmac

from typing import Any, AnyStr, Callable, Optional, Union, overload
from types import ModuleType
import sys

_B = Union[bytes, bytearray]

# TODO more precise type for object of hashlib
_Hash = Any

digest_size: None

if sys.version_info >= (3, 8):
    _DigestMod = Union[str, Callable[[], _Hash], ModuleType]
    # In reality digestmod has a default value, but the function always throws an error
    # if the argument is not given, so we pretend it is a required argument.
    @overload
    def new(key: _B, msg: Optional[_B], digestmod: _DigestMod) -> HMAC: ...
    @overload
    def new(key: _B, *, digestmod: _DigestMod) -> HMAC: ...
elif sys.version_info >= (3, 4):
    def new(key: _B, msg: Optional[_B] = ...,
            digestmod: Optional[Union[str, Callable[[], _Hash], ModuleType]] = ...) -> HMAC: ...
else:
    def new(key: _B, msg: Optional[_B] = ...,
            digestmod: Optional[Union[Callable[[], _Hash], ModuleType]] = ...) -> HMAC: ...

class HMAC:
    if sys.version_info >= (3,):
        digest_size: int
    if sys.version_info >= (3, 4):
        block_size: int
        name: str
    def update(self, msg: _B) -> None: ...
    def digest(self) -> bytes: ...
    def hexdigest(self) -> str: ...
    def copy(self) -> HMAC: ...

@overload
def compare_digest(a: bytearray, b: bytearray) -> bool: ...
@overload
def compare_digest(a: AnyStr, b: AnyStr) -> bool: ...

if sys.version_info >= (3, 7):
    def digest(key: _B, msg: _B, digest: str) -> bytes: ...
