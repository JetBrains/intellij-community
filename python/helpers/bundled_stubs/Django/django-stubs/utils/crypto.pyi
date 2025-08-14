from collections.abc import Callable
from hmac import HMAC

RANDOM_STRING_CHARS: str

class InvalidAlgorithm(ValueError): ...

def salted_hmac(
    key_salt: bytes | str, value: bytes | str, secret: bytes | str | None = None, *, algorithm: str = "sha1"
) -> HMAC: ...
def get_random_string(length: int, allowed_chars: str = ...) -> str: ...
def constant_time_compare(val1: bytes | str, val2: bytes | str) -> bool: ...
def pbkdf2(
    password: bytes | str,
    salt: bytes | str,
    iterations: int,
    dklen: int = 0,
    digest: Callable | None = None,
) -> bytes: ...
