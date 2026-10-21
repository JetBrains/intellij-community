from collections.abc import Callable
from hmac import HMAC
from typing import Any, overload

from typing_extensions import deprecated

RANDOM_STRING_CHARS: str

class InvalidAlgorithm(ValueError): ...

@overload
@deprecated(
    "The default argument for algorithm in salted_hmac() will change from 'sha1' to 'sha256' in "
    "Django 7.0. Pass an explicit algorithm to silence this warning.",
)
def salted_hmac(
    key_salt: bytes | str, value: bytes | str, secret: bytes | str | None = None, *, algorithm: None = None
) -> HMAC: ...
@overload
def salted_hmac(
    key_salt: bytes | str, value: bytes | str, secret: bytes | str | None = None, *, algorithm: str = ...
) -> HMAC: ...
def get_random_string(length: int, allowed_chars: str = ...) -> str: ...
def constant_time_compare(val1: bytes | str, val2: bytes | str) -> bool: ...
def pbkdf2(
    password: bytes | str,
    salt: bytes | str,
    iterations: int,
    dklen: int = 0,
    digest: Callable[[], Any] | None = None,
) -> bytes: ...
