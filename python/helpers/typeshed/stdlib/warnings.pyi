from types import ModuleType, TracebackType
from typing import Any, Sequence, TextIO, Type, overload
from typing_extensions import Literal

from _warnings import warn as warn, warn_explicit as warn_explicit

filters: Sequence[tuple[str, str | None, Type[Warning], str | None, int]]  # undocumented, do not mutate

def showwarning(
    message: Warning | str, category: Type[Warning], filename: str, lineno: int, file: TextIO | None = ..., line: str | None = ...
) -> None: ...
def formatwarning(message: Warning | str, category: Type[Warning], filename: str, lineno: int, line: str | None = ...) -> str: ...
def filterwarnings(
    action: str, message: str = ..., category: Type[Warning] = ..., module: str = ..., lineno: int = ..., append: bool = ...
) -> None: ...
def simplefilter(action: str, category: Type[Warning] = ..., lineno: int = ..., append: bool = ...) -> None: ...
def resetwarnings() -> None: ...

class _OptionError(Exception): ...

class WarningMessage:
    message: Warning | str
    category: Type[Warning]
    filename: str
    lineno: int
    file: TextIO | None
    line: str | None
    source: Any | None
    def __init__(
        self,
        message: Warning | str,
        category: Type[Warning],
        filename: str,
        lineno: int,
        file: TextIO | None = ...,
        line: str | None = ...,
        source: Any | None = ...,
    ) -> None: ...

class catch_warnings:
    @overload
    def __new__(cls, *, record: Literal[False] = ..., module: ModuleType | None = ...) -> _catch_warnings_without_records: ...
    @overload
    def __new__(cls, *, record: Literal[True], module: ModuleType | None = ...) -> _catch_warnings_with_records: ...
    @overload
    def __new__(cls, *, record: bool, module: ModuleType | None = ...) -> catch_warnings: ...
    def __enter__(self) -> list[WarningMessage] | None: ...
    def __exit__(
        self, exc_type: Type[BaseException] | None, exc_val: BaseException | None, exc_tb: TracebackType | None
    ) -> None: ...

class _catch_warnings_without_records(catch_warnings):
    def __enter__(self) -> None: ...

class _catch_warnings_with_records(catch_warnings):
    def __enter__(self) -> list[WarningMessage]: ...
