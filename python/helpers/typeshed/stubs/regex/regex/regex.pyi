from typing import Any, AnyStr, Callable, overload

from . import _regex
from ._regex_core import *

__version__: str

def compile(
    pattern: AnyStr | _regex.Pattern[AnyStr], flags: int = ..., ignore_unused: bool = ..., **kwargs: Any
) -> _regex.Pattern[AnyStr]: ...
def search(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    string: AnyStr,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Match[AnyStr] | None: ...
def match(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    string: AnyStr,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Match[AnyStr] | None: ...
def fullmatch(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    string: AnyStr,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Match[AnyStr] | None: ...
def split(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    string: AnyStr,
    maxsplit: int = ...,
    flags: int = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> list[AnyStr | Any]: ...
def splititer(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    string: AnyStr,
    maxsplit: int = ...,
    flags: int = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Splitter[AnyStr]: ...
def findall(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    string: AnyStr,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    overlapped: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> list[Any]: ...
def finditer(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    string: AnyStr,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    overlapped: bool = ...,
    partial: bool = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> _regex.Scanner[AnyStr]: ...
def sub(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    repl: AnyStr | Callable[[_regex.Match[AnyStr]], AnyStr],
    string: AnyStr,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> AnyStr: ...
def subf(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    format: AnyStr | Callable[[_regex.Match[AnyStr]], AnyStr],
    string: AnyStr,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> AnyStr: ...
def subn(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    repl: AnyStr | Callable[[_regex.Match[AnyStr]], AnyStr],
    string: AnyStr,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> tuple[AnyStr, int]: ...
def subfn(
    pattern: AnyStr | _regex.Pattern[AnyStr],
    format: AnyStr | Callable[[_regex.Match[AnyStr]], AnyStr],
    string: AnyStr,
    count: int = ...,
    flags: int = ...,
    pos: int | None = ...,
    endpos: int | None = ...,
    concurrent: bool | None = ...,
    timeout: float | None = ...,
    ignore_unused: bool = ...,
    **kwargs: Any,
) -> tuple[AnyStr, int]: ...
def purge() -> None: ...
@overload
def cache_all(value: bool = ...) -> None: ...
@overload
def cache_all(value: None) -> bool: ...
def escape(pattern: AnyStr, special_only: bool = ..., literal_spaces: bool = ...) -> AnyStr: ...
def template(pattern: AnyStr | _regex.Pattern[AnyStr], flags: int = ...) -> _regex.Pattern[AnyStr]: ...

Pattern = _regex.Pattern[AnyStr]
Match = _regex.Match[AnyStr]
Regex = compile
