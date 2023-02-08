from _typeshed import Self
from collections.abc import Callable, Mapping
from typing import Any, AnyStr, Generic, TypeVar, overload
from typing_extensions import Literal, final

_T = TypeVar("_T")

@final
class Pattern(Generic[AnyStr]):
    pattern: AnyStr
    flags: int
    groups: int
    groupindex: Mapping[str, int]
    named_lists: Mapping[str, frozenset[AnyStr]]
    def search(
        self,
        string: AnyStr,
        pos: int | None = ...,
        endpos: int | None = ...,
        concurrent: bool | None = ...,
        timeout: float | None = ...,
    ) -> Match[AnyStr] | None: ...
    def match(
        self,
        string: AnyStr,
        pos: int | None = ...,
        endpos: int | None = ...,
        concurrent: bool | None = ...,
        timeout: float | None = ...,
    ) -> Match[AnyStr] | None: ...
    def fullmatch(
        self,
        string: AnyStr,
        pos: int | None = ...,
        endpos: int | None = ...,
        concurrent: bool | None = ...,
        timeout: float | None = ...,
    ) -> Match[AnyStr] | None: ...
    def split(
        self, string: AnyStr, maxsplit: int = ..., concurrent: bool | None = ..., timeout: float | None = ...
    ) -> list[AnyStr | Any]: ...
    def splititer(
        self, string: AnyStr, maxsplit: int = ..., concurrent: bool | None = ..., timeout: float | None = ...
    ) -> Splitter[AnyStr]: ...
    def findall(
        self,
        string: AnyStr,
        pos: int | None = ...,
        endpos: int | None = ...,
        overlapped: bool = ...,
        concurrent: bool | None = ...,
        timeout: float | None = ...,
    ) -> list[Any]: ...
    def finditer(
        self,
        string: AnyStr,
        pos: int | None = ...,
        endpos: int | None = ...,
        overlapped: bool = ...,
        concurrent: bool | None = ...,
        timeout: float | None = ...,
    ) -> Scanner[AnyStr]: ...
    def sub(
        self,
        repl: AnyStr | Callable[[Match[AnyStr]], AnyStr],
        string: AnyStr,
        count: int = ...,
        flags: int = ...,
        pos: int | None = ...,
        endpos: int | None = ...,
        concurrent: bool | None = ...,
        timeout: float | None = ...,
    ) -> AnyStr: ...
    def subf(
        self,
        format: AnyStr | Callable[[Match[AnyStr]], AnyStr],
        string: AnyStr,
        count: int = ...,
        flags: int = ...,
        pos: int | None = ...,
        endpos: int | None = ...,
        concurrent: bool | None = ...,
        timeout: float | None = ...,
    ) -> AnyStr: ...
    def subn(
        self,
        repl: AnyStr | Callable[[Match[AnyStr]], AnyStr],
        string: AnyStr,
        count: int = ...,
        flags: int = ...,
        pos: int | None = ...,
        endpos: int | None = ...,
        concurrent: bool | None = ...,
        timeout: float | None = ...,
    ) -> tuple[AnyStr, int]: ...
    def subfn(
        self,
        format: AnyStr | Callable[[Match[AnyStr]], AnyStr],
        string: AnyStr,
        count: int = ...,
        flags: int = ...,
        pos: int | None = ...,
        endpos: int | None = ...,
        concurrent: bool | None = ...,
        timeout: float | None = ...,
    ) -> tuple[AnyStr, int]: ...
    def scanner(
        self,
        string: AnyStr,
        pos: int | None = ...,
        endpos: int | None = ...,
        overlapped: bool = ...,
        concurrent: bool | None = ...,
        timeout: float | None = ...,
    ) -> Scanner[AnyStr]: ...

@final
class Match(Generic[AnyStr]):

    re: Pattern[AnyStr]
    string: AnyStr
    pos: int
    endpos: int
    partial: bool
    regs: tuple[tuple[int, int], ...]
    fuzzy_counts: tuple[int, int, int]
    fuzzy_changes: tuple[list[int], list[int], list[int]]
    lastgroup: str | None
    lastindex: int | None
    @overload
    def group(self, __group: Literal[0] = ...) -> AnyStr: ...
    @overload
    def group(self, __group: int | str = ...) -> AnyStr | Any: ...
    @overload
    def group(self, __group1: int | str, __group2: int | str, *groups: int | str) -> tuple[AnyStr | Any, ...]: ...
    @overload
    def groups(self, default: None = ...) -> tuple[AnyStr | Any, ...]: ...
    @overload
    def groups(self, default: _T) -> tuple[AnyStr | _T, ...]: ...
    @overload
    def groupdict(self, default: None = ...) -> dict[str, AnyStr | Any]: ...
    @overload
    def groupdict(self, default: _T) -> dict[str, AnyStr | _T]: ...
    @overload
    def span(self, __group: int | str = ...) -> tuple[int, int]: ...
    @overload
    def span(self, __group1: int | str, __group2: int | str, *groups: int | str) -> tuple[tuple[int, int], ...]: ...
    @overload
    def spans(self, __group: int | str = ...) -> list[tuple[int, int]]: ...
    @overload
    def spans(self, __group1: int | str, __group2: int | str, *groups: int | str) -> tuple[list[tuple[int, int]], ...]: ...
    @overload
    def start(self, __group: int | str = ...) -> int: ...
    @overload
    def start(self, __group1: int | str, __group2: int | str, *groups: int | str) -> tuple[int, ...]: ...
    @overload
    def starts(self, __group: int | str = ...) -> list[int]: ...
    @overload
    def starts(self, __group1: int | str, __group2: int | str, *groups: int | str) -> tuple[list[int], ...]: ...
    @overload
    def end(self, __group: int | str = ...) -> int: ...
    @overload
    def end(self, __group1: int | str, __group2: int | str, *groups: int | str) -> tuple[int, ...]: ...
    @overload
    def ends(self, __group: int | str = ...) -> list[int]: ...
    @overload
    def ends(self, __group1: int | str, __group2: int | str, *groups: int | str) -> tuple[list[int], ...]: ...
    def expand(self, template: AnyStr) -> AnyStr: ...
    def expandf(self, format: AnyStr) -> AnyStr: ...
    @overload
    def captures(self, __group: int | str = ...) -> list[AnyStr]: ...
    @overload
    def captures(self, __group1: int | str, __group2: int | str, *groups: int | str) -> tuple[list[AnyStr], ...]: ...
    def capturesdict(self) -> dict[str, list[AnyStr]]: ...
    def detach_string(self) -> None: ...
    @overload
    def __getitem__(self, __key: Literal[0]) -> AnyStr: ...
    @overload
    def __getitem__(self, __key: int | str) -> AnyStr | Any: ...

@final
class Splitter(Generic[AnyStr]):

    pattern: Pattern[AnyStr]
    def __iter__(self: Self) -> Self: ...
    def __next__(self) -> AnyStr | Any: ...
    def split(self) -> AnyStr | Any: ...

@final
class Scanner(Generic[AnyStr]):

    pattern: Pattern[AnyStr]
    def __iter__(self: Self) -> Self: ...
    def __next__(self) -> Match[AnyStr]: ...
    def match(self) -> Match[AnyStr] | None: ...
    def search(self) -> Match[AnyStr] | None: ...
