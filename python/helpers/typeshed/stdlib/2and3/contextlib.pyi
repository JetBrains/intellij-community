# Stubs for contextlib

from typing import (
    Any, Callable, Generator, IO, Iterable, Iterator, Optional, Type,
    Generic, TypeVar,
)
from types import TracebackType
import sys

_T = TypeVar('_T')
_ExitFunc = Callable[[Optional[Type[BaseException]],
                      Optional[Exception],
                      Optional[TracebackType]], bool]
_CM_EF = TypeVar('_CM_EF', ContextManager, _ExitFunc)

# TODO already in PEP, have to get added to mypy
class ContextManager(Generic[_T]):
    def __enter__(self) -> _T: ...
    def __exit__(self, exc_type: Optional[Type[BaseException]],
                 exc_val: Optional[Exception],
                 exc_tb: Optional[TracebackType]) -> bool: ...

def contextmanager(func: Callable[..., Iterator[_T]]) -> Callable[..., ContextManager[_T]]: ...

if sys.version_info < (3,):
    def nested(*mgr: ContextManager[Any]) -> ContextManager[Iterable[Any]]: ...

class closing(Generic[_T], ContextManager[_T]):
    def __init__(self, thing: _T) -> None: ...

if sys.version_info >= (3, 4):
    class suppress(ContextManager[None]):
        def __init__(self, *exceptions: Type[BaseException]) -> None: ...

    class redirect_stdout(ContextManager[None]):
        def __init__(self, new_target: IO[str]) -> None: ...

if sys.version_info >= (3, 5):
    class redirect_stderr(ContextManager[None]):
        def __init__(self, new_target: IO[str]) -> None: ...

if sys.version_info >= (3,):
    class ContextDecorator:
        def __call__(self, func: Callable[..., None]) -> Callable[..., ContextManager[None]]: ...

    class ExitStack(ContextManager[ExitStack]):
        def __init__(self) -> None: ...
        def enter_context(self, cm: ContextManager[_T]) -> _T: ...
        def push(self, exit: _CM_EF) -> _CM_EF: ...
        def callback(self, callback: Callable[..., None],
                     *args: Any, **kwds: Any) -> Callable[..., None]: ...
        def pop_all(self) -> ExitStack: ...
        def close(self) -> None: ...
