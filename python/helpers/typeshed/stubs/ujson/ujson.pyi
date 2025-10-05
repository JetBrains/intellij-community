from _typeshed import SupportsRead, SupportsWrite
from collections.abc import Callable
from typing import Any, Final

__version__: Final[str]

def encode(
    obj: Any,
    ensure_ascii: bool = ...,
    double_precision: int = ...,
    encode_html_chars: bool = ...,
    escape_forward_slashes: bool = ...,
    sort_keys: bool = ...,
    indent: int = ...,
    allow_nan: bool = ...,
    reject_bytes: bool = ...,
    default: Callable[[Any], Any] | None = None,  # Specify how to serialize arbitrary types
    separators: tuple[str, str] | None = None,
) -> str: ...
def dumps(
    obj: Any,
    ensure_ascii: bool = ...,
    double_precision: int = ...,
    encode_html_chars: bool = ...,
    escape_forward_slashes: bool = ...,
    sort_keys: bool = ...,
    indent: int = ...,
    allow_nan: bool = ...,
    reject_bytes: bool = ...,
    default: Callable[[Any], Any] | None = None,  # Specify how to serialize arbitrary types
    separators: tuple[str, str] | None = None,
) -> str: ...
def dump(
    obj: Any,
    fp: SupportsWrite[str],
    *,
    ensure_ascii: bool = ...,
    double_precision: int = ...,
    encode_html_chars: bool = ...,
    escape_forward_slashes: bool = ...,
    sort_keys: bool = ...,
    indent: int = ...,
    allow_nan: bool = ...,
    reject_bytes: bool = ...,
    default: Callable[[Any], Any] | None = None,  # Specify how to serialize arbitrary types
    separators: tuple[str, str] | None = None,
) -> None: ...
def decode(s: str | bytes | bytearray, precise_float: bool = ...) -> Any: ...
def loads(s: str | bytes | bytearray, precise_float: bool = ...) -> Any: ...
def load(fp: SupportsRead[str | bytes | bytearray], precise_float: bool = ...) -> Any: ...

class JSONDecodeError(ValueError): ...
