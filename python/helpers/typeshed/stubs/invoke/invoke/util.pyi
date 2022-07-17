import threading
from contextlib import AbstractContextManager
from logging import Logger
from types import TracebackType
from typing import Any, Callable, Iterable, Mapping, NamedTuple

LOG_FORMAT: str

def enable_logging() -> None: ...

log: Logger

def task_name_sort_key(name: str) -> tuple[list[str], str]: ...
def cd(where: str) -> AbstractContextManager[None]: ...
def has_fileno(stream) -> bool: ...
def isatty(stream) -> bool: ...
def encode_output(string: str, encoding: str) -> str: ...
def helpline(obj: Callable[..., Any]) -> str | None: ...

class ExceptionHandlingThread(threading.Thread):
    def __init__(
        self,
        *,
        group: None = ...,
        target: Callable[..., Any] | None = ...,
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
