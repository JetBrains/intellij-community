from _typeshed import ReadableBuffer
from collections.abc import Callable
from typing import Any, AnyStr, overload

from . import _regex
from ._regex import Match as Match, Pattern as Pattern
from ._regex_core import *

__version__: str

def compile(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    flags: int = ...,
    ignore_unused: bool = ...,
    cache_pattern: bool | None = ...,
    **kwargs: Any,
) -> _regex.Pattern[AnyStr]: ...
@overload
def search(
    pattern: str | Pattern[str],
    string: str,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Match[str] | None: ...
@overload
def search(
    pattern: bytes | Pattern[bytes],
    string: ReadableBuffer,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Match[bytes] | None: ...
@overload
def match(
    pattern: str | Pattern[str],
    string: str,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Match[str] | None: ...
@overload
def match(
    pattern: bytes | Pattern[bytes],
    string: ReadableBuffer,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Match[bytes] | None: ...
@overload
def fullmatch(
    pattern: str | Pattern[str],
    string: str,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Match[str] | None: ...
@overload
def fullmatch(
    pattern: bytes | Pattern[bytes],
    string: ReadableBuffer,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Match[bytes] | None: ...
@overload
def split(
    pattern: str | _regex.Pattern[str],
    string: str,
    maxsplit: int = ...,
    flags: int = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> list[str | Any]: ...
@overload
def split(
    pattern: ReadableBuffer | _regex.Pattern[bytes],
    string: ReadableBuffer,
    maxsplit: int = ...,
    flags: int = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> list[bytes | Any]: ...
@overload
def splititer(
    pattern: str | _regex.Pattern[str],
    string: str,
    maxsplit: int = ...,
    flags: int = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Splitter[str]: ...
@overload
def splititer(
    pattern: ReadableBuffer | _regex.Pattern[bytes],
    string: ReadableBuffer,
    maxsplit: int = ...,
    flags: int = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Splitter[bytes]: ...
@overload
def findall(
    pattern: str | _regex.Pattern[str],
    string: str,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    overlapped: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> list[Any]: ...
@overload
def findall(
    pattern: ReadableBuffer | _regex.Pattern[bytes],
    string: ReadableBuffer,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    overlapped: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> list[Any]: ...
@overload
def finditer(
    pattern: str | _regex.Pattern[str],
    string: str,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    overlapped: bool = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Scanner[str]: ...
@overload
def finditer(
    pattern: ReadableBuffer | _regex.Pattern[bytes],
    string: ReadableBuffer,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    overlapped: bool = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Scanner[bytes]: ...
@overload
def sub(
    pattern: str | _regex.Pattern[str],
    repl: str | Callable[[_regex.Match[str]], str],
    string: str,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> str: ...
@overload
def sub(
    pattern: ReadableBuffer | _regex.Pattern[bytes],
    repl: ReadableBuffer | Callable[[_regex.Match[bytes]], ReadableBuffer],
    string: ReadableBuffer,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> bytes: ...
@overload
def subf(
    pattern: str | _regex.Pattern[str],
    format: str | Callable[[_regex.Match[str]], str],
    string: str,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> str: ...
@overload
def subf(
    pattern: ReadableBuffer | _regex.Pattern[bytes],
    format: ReadableBuffer | Callable[[_regex.Match[bytes]], ReadableBuffer],
    string: ReadableBuffer,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> bytes: ...
@overload
def subn(
    pattern: str | _regex.Pattern[str],
    repl: str | Callable[[_regex.Match[str]], str],
    string: str,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> tuple[str, int]: ...
@overload
def subn(
    pattern: ReadableBuffer | _regex.Pattern[bytes],
    repl: ReadableBuffer | Callable[[_regex.Match[bytes]], ReadableBuffer],
    string: ReadableBuffer,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> tuple[bytes, int]: ...
@overload
def subfn(
    pattern: str | _regex.Pattern[str],
    format: str | Callable[[_regex.Match[str]], str],
    string: str,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> tuple[str, int]: ...
@overload
def subfn(
    pattern: ReadableBuffer | _regex.Pattern[bytes],
    format: ReadableBuffer | Callable[[_regex.Match[bytes]], ReadableBuffer],
    string: ReadableBuffer,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> tuple[bytes, int]: ...
def purge() -> None: ...
@overload
def cache_all(value: bool = ...) -> None: ...
@overload
def cache_all(value: None) -> bool: ...
def escape(pattern: AnyStr, special_only: bool = ..., literal_spaces: bool = ...) -> AnyStr: ...
def template(pattern: AnyStr | _regex.Pattern[AnyStr], flags: int = ...) -> _regex.Pattern[AnyStr]: ...

Regex = compile
