import sys
from _typeshed import Self, StrOrBytesPath
from collections.abc import AsyncGenerator, AsyncIterator, Awaitable, Callable, Generator, Iterator
from types import TracebackType
from typing import IO, Any, AsyncContextManager, ContextManager, Generic, Protocol, TypeVar, overload  # noqa: Y022,Y027
from typing_extensions import ParamSpec, TypeAlias

__all__ = [
    "contextmanager",
    "closing",
    "AbstractContextManager",
    "ContextDecorator",
    "ExitStack",
    "redirect_stdout",
    "redirect_stderr",
    "suppress",
    "AbstractAsyncContextManager",
    "AsyncExitStack",
    "asynccontextmanager",
    "nullcontext",
]

if sys.version_info >= (3, 10):
    __all__ += ["aclosing"]

if sys.version_info >= (3, 11):
    __all__ += ["chdir"]

_T = TypeVar("_T")
_T_co = TypeVar("_T_co", covariant=True)
_T_io = TypeVar("_T_io", bound=IO[str] | None)
_F = TypeVar("_F", bound=Callable[..., Any])
_P = ParamSpec("_P")

_ExitFunc: TypeAlias = Callable[[type[BaseException] | None, BaseException | None, TracebackType | None], bool | None]
_CM_EF = TypeVar("_CM_EF", bound=AbstractContextManager[Any] | _ExitFunc)

AbstractContextManager = ContextManager
AbstractAsyncContextManager = AsyncContextManager

class ContextDecorator:
    def __call__(self, func: _F) -> _F: ...

class _GeneratorContextManager(AbstractContextManager[_T_co], ContextDecorator, Generic[_T_co]):
    # __init__ and all instance attributes are actually inherited from _GeneratorContextManagerBase
    # _GeneratorContextManagerBase is more trouble than it's worth to include in the stub; see #6676
    def __init__(self, func: Callable[..., Iterator[_T_co]], args: tuple[Any, ...], kwds: dict[str, Any]) -> None: ...
    gen: Generator[_T_co, Any, Any]
    func: Callable[..., Generator[_T_co, Any, Any]]
    args: tuple[Any, ...]
    kwds: dict[str, Any]
    def __exit__(
        self, typ: type[BaseException] | None, value: BaseException | None, traceback: TracebackType | None
    ) -> bool | None: ...

def contextmanager(func: Callable[_P, Iterator[_T_co]]) -> Callable[_P, _GeneratorContextManager[_T_co]]: ...

if sys.version_info >= (3, 10):
    _AF = TypeVar("_AF", bound=Callable[..., Awaitable[Any]])

    class AsyncContextDecorator:
        def __call__(self, func: _AF) -> _AF: ...

    class _AsyncGeneratorContextManager(AbstractAsyncContextManager[_T_co], AsyncContextDecorator, Generic[_T_co]):
        # __init__ and these attributes are actually defined in the base class _GeneratorContextManagerBase,
        # which is more trouble than it's worth to include in the stub (see #6676)
        def __init__(self, func: Callable[..., AsyncIterator[_T_co]], args: tuple[Any, ...], kwds: dict[str, Any]) -> None: ...
        gen: AsyncGenerator[_T_co, Any]
        func: Callable[..., AsyncGenerator[_T_co, Any]]
        args: tuple[Any, ...]
        kwds: dict[str, Any]
        async def __aexit__(
            self, typ: type[BaseException] | None, value: BaseException | None, traceback: TracebackType | None
        ) -> bool | None: ...

else:
    class _AsyncGeneratorContextManager(AbstractAsyncContextManager[_T_co], Generic[_T_co]):
        def __init__(self, func: Callable[..., AsyncIterator[_T_co]], args: tuple[Any, ...], kwds: dict[str, Any]) -> None: ...
        gen: AsyncGenerator[_T_co, Any]
        func: Callable[..., AsyncGenerator[_T_co, Any]]
        args: tuple[Any, ...]
        kwds: dict[str, Any]
        async def __aexit__(
            self, typ: type[BaseException] | None, value: BaseException | None, traceback: TracebackType | None
        ) -> bool | None: ...

def asynccontextmanager(func: Callable[_P, AsyncIterator[_T_co]]) -> Callable[_P, _AsyncGeneratorContextManager[_T_co]]: ...

class _SupportsClose(Protocol):
    def close(self) -> object: ...

_SupportsCloseT = TypeVar("_SupportsCloseT", bound=_SupportsClose)

class closing(AbstractContextManager[_SupportsCloseT]):
    def __init__(self, thing: _SupportsCloseT) -> None: ...
    def __exit__(self, *exc_info: object) -> None: ...

if sys.version_info >= (3, 10):
    class _SupportsAclose(Protocol):
        def aclose(self) -> Awaitable[object]: ...
    _SupportsAcloseT = TypeVar("_SupportsAcloseT", bound=_SupportsAclose)

    class aclosing(AbstractAsyncContextManager[_SupportsAcloseT]):
        def __init__(self, thing: _SupportsAcloseT) -> None: ...
        async def __aexit__(self, *exc_info: object) -> None: ...

class suppress(AbstractContextManager[None]):
    def __init__(self, *exceptions: type[BaseException]) -> None: ...
    def __exit__(
        self, exctype: type[BaseException] | None, excinst: BaseException | None, exctb: TracebackType | None
    ) -> bool: ...

class _RedirectStream(AbstractContextManager[_T_io]):
    def __init__(self, new_target: _T_io) -> None: ...
    def __exit__(
        self, exctype: type[BaseException] | None, excinst: BaseException | None, exctb: TracebackType | None
    ) -> None: ...

class redirect_stdout(_RedirectStream[_T_io]): ...
class redirect_stderr(_RedirectStream[_T_io]): ...

class ExitStack:
    def __init__(self) -> None: ...
    def enter_context(self, cm: AbstractContextManager[_T]) -> _T: ...
    def push(self, exit: _CM_EF) -> _CM_EF: ...
    def callback(self, __callback: Callable[_P, _T], *args: _P.args, **kwds: _P.kwargs) -> Callable[_P, _T]: ...
    def pop_all(self: Self) -> Self: ...
    def close(self) -> None: ...
    def __enter__(self: Self) -> Self: ...
    def __exit__(
        self, __exc_type: type[BaseException] | None, __exc_value: BaseException | None, __traceback: TracebackType | None
    ) -> bool: ...

_ExitCoroFunc: TypeAlias = Callable[[type[BaseException] | None, BaseException | None, TracebackType | None], Awaitable[bool]]
_ACM_EF = TypeVar("_ACM_EF", bound=AbstractAsyncContextManager[Any] | _ExitCoroFunc)

class AsyncExitStack:
    def __init__(self) -> None: ...
    def enter_context(self, cm: AbstractContextManager[_T]) -> _T: ...
    async def enter_async_context(self, cm: AbstractAsyncContextManager[_T]) -> _T: ...
    def push(self, exit: _CM_EF) -> _CM_EF: ...
    def push_async_exit(self, exit: _ACM_EF) -> _ACM_EF: ...
    def callback(self, __callback: Callable[_P, _T], *args: _P.args, **kwds: _P.kwargs) -> Callable[_P, _T]: ...
    def push_async_callback(
        self, __callback: Callable[_P, Awaitable[_T]], *args: _P.args, **kwds: _P.kwargs
    ) -> Callable[_P, Awaitable[_T]]: ...
    def pop_all(self: Self) -> Self: ...
    async def aclose(self) -> None: ...
    async def __aenter__(self: Self) -> Self: ...
    async def __aexit__(
        self, __exc_type: type[BaseException] | None, __exc_value: BaseException | None, __traceback: TracebackType | None
    ) -> bool: ...

if sys.version_info >= (3, 10):
    class nullcontext(AbstractContextManager[_T], AbstractAsyncContextManager[_T]):
        enter_result: _T
        @overload
        def __init__(self: nullcontext[None], enter_result: None = ...) -> None: ...
        @overload
        def __init__(self: nullcontext[_T], enter_result: _T) -> None: ...
        def __enter__(self) -> _T: ...
        def __exit__(self, *exctype: object) -> None: ...
        async def __aenter__(self) -> _T: ...
        async def __aexit__(self, *exctype: object) -> None: ...

else:
    class nullcontext(AbstractContextManager[_T]):
        enter_result: _T
        @overload
        def __init__(self: nullcontext[None], enter_result: None = ...) -> None: ...
        @overload
        def __init__(self: nullcontext[_T], enter_result: _T) -> None: ...
        def __enter__(self) -> _T: ...
        def __exit__(self, *exctype: object) -> None: ...

if sys.version_info >= (3, 11):
    _T_fd_or_any_path = TypeVar("_T_fd_or_any_path", bound=int | StrOrBytesPath)

    class chdir(AbstractContextManager[None], Generic[_T_fd_or_any_path]):
        path: _T_fd_or_any_path
        def __init__(self, path: _T_fd_or_any_path) -> None: ...
        def __enter__(self) -> None: ...
        def __exit__(self, *excinfo: object) -> None: ...
