# Stubs for contextlib

from typing import (
    Any, Callable, Generator, IO, Iterable, Iterator, Optional, Type,
    Generic, TypeVar
)
from types import TracebackType
import sys
# Aliased here for backwards compatibility; TODO eventually remove this
from typing import ContextManager as ContextManager

if sys.version_info >= (3, 5):
    from typing import AsyncContextManager, AsyncIterator

if sys.version_info >= (3, 6):
    from typing import ContextManager as AbstractContextManager
if sys.version_info >= (3, 7):
    from typing import AsyncContextManager as AbstractAsyncContextManager

_T = TypeVar('_T')

_ExitFunc = Callable[[Optional[Type[BaseException]],
                      Optional[BaseException],
                      Optional[TracebackType]], bool]
_CM_EF = TypeVar('_CM_EF', ContextManager, _ExitFunc)

if sys.version_info >= (3, 2):
    class GeneratorContextManager(ContextManager[_T], Generic[_T]):
        def __call__(self, func: Callable[..., _T]) -> Callable[..., _T]: ...
    def contextmanager(func: Callable[..., Iterator[_T]]) -> Callable[..., GeneratorContextManager[_T]]: ...
else:
    def contextmanager(func: Callable[..., Iterator[_T]]) -> Callable[..., ContextManager[_T]]: ...

if sys.version_info >= (3, 7):
    def asynccontextmanager(func: Callable[..., AsyncIterator[_T]]) -> Callable[..., AsyncContextManager[_T]]: ...

if sys.version_info < (3,):
    def nested(*mgr: ContextManager[Any]) -> ContextManager[Iterable[Any]]: ...

class closing(ContextManager[_T], Generic[_T]):
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

    _U = TypeVar('_U', bound='ExitStack')

    class ExitStack(ContextManager[ExitStack]):
        def __init__(self) -> None: ...
        def enter_context(self, cm: ContextManager[_T]) -> _T: ...
        def push(self, exit: _CM_EF) -> _CM_EF: ...
        def callback(self, callback: Callable[..., Any],
                     *args: Any, **kwds: Any) -> Callable[..., Any]: ...
        def pop_all(self: _U) -> _U: ...
        def close(self) -> None: ...
        def __enter__(self: _U) -> _U: ...

if sys.version_info >= (3, 7):
    from typing import Awaitable

    _S = TypeVar('_S', bound='AsyncExitStack')

    _ExitCoroFunc = Callable[[Optional[Type[BaseException]],
                              Optional[BaseException],
                              Optional[TracebackType]], Awaitable[bool]]
    _CallbackCoroFunc = Callable[..., Awaitable[Any]]
    _ACM_EF = TypeVar('_ACM_EF', AsyncContextManager, _ExitCoroFunc)

    class AsyncExitStack(AsyncContextManager[AsyncExitStack]):
        def __init__(self) -> None: ...
        def enter_context(self, cm: ContextManager[_T]) -> _T: ...
        def enter_async_context(self, cm: AsyncContextManager[_T]) -> Awaitable[_T]: ...
        def push(self, exit: _CM_EF) -> _CM_EF: ...
        def push_async_exit(self, exit: _ACM_EF) -> _ACM_EF: ...
        def callback(self, callback: Callable[..., Any],
                     *args: Any, **kwds: Any) -> Callable[..., Any]: ...
        def push_async_callback(self, callback: _CallbackCoroFunc,
                                *args: Any, **kwds: Any) -> _CallbackCoroFunc: ...
        def pop_all(self: _S) -> _S: ...
        def aclose(self) -> Awaitable[None]: ...
        def __aenter__(self: _S) -> Awaitable[_S]: ...
