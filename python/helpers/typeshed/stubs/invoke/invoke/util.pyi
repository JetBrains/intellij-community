import threading
from collections.abc import Callable, Iterable, Mapping
from contextlib import AbstractContextManager
from logging import Logger
from types import TracebackType
from typing import Any, NamedTuple

LOG_FORMAT: str

def enable_logging() -> None: ...

log: Logger

def task_name_sort_key(name: str) -> tuple[list[str], str]: ...
def cd(where: str) -> AbstractContextManager[None]: ...
def has_fileno(stream) -> bool: ...
def isatty(stream) -> bool: ...
def encode_output(string: str, encoding: str) -> str: ...
def helpline(obj: Callable[..., object]) -> str | None: ...

class ExceptionHandlingThread(threading.Thread):
    def __init__(
        self,
        *,
        group: None = ...,
        target: Callable[..., object] | None = ...,
        name: str | None = ...,
        args: Iterable[Any] = ...,
        kwargs: Mapping[str, Any] | None = ...,
        daemon: bool | None = ...,
    ) -> None: ...
    def exception(self) -> ExceptionWrapper | None: ...
    @property
    def is_dead(self) -> bool: ...

class ExceptionWrapper(NamedTuple):
    kwargs: Any
    type: type[BaseException]
    value: BaseException
    traceback: TracebackType
