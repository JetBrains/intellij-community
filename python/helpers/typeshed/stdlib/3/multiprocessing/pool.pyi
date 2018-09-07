from typing import (
    Any, Callable, ContextManager, Iterable, Mapping, Optional, List,
    TypeVar, Generic,
)

_PT = TypeVar('_PT', bound='Pool')
_S = TypeVar('_S')
_T = TypeVar('_T')

class AsyncResult(Generic[_T]):
    def get(self, timeout: Optional[float] = ...) -> _T: ...
    def wait(self, timeout: Optional[float] = ...) -> None: ...
    def ready(self) -> bool: ...
    def successful(self) -> bool: ...

_IMIT = TypeVar('_IMIT', bound=IMapIterator)

class IMapIterator(Iterable[_T]):
    def __iter__(self: _IMIT) -> _IMIT: ...
    def next(self, timeout: Optional[float] = ...) -> _T: ...
    def __next__(self, timeout: Optional[float] = ...) -> _T: ...

class Pool(ContextManager[Pool]):
    def __init__(self, processes: Optional[int] = ...,
                 initializer: Optional[Callable[..., None]] = ...,
                 initargs: Iterable[Any] = ...,
                 maxtasksperchild: Optional[int] = ...,
                 context: Optional[Any] = ...) -> None: ...
    def apply(self,
              func: Callable[..., _T],
              args: Iterable[Any] = ...,
              kwds: Mapping[str, Any] = ...) -> _T: ...
    def apply_async(self,
                func: Callable[..., _T],
                args: Iterable[Any] = ...,
                kwds: Mapping[str, Any] = ...,
                callback: Optional[Callable[[_T], None]] = ...,
                error_callback: Optional[Callable[[BaseException], None]] = ...) -> AsyncResult[_T]: ...
    def map(self,
            func: Callable[[_S], _T],
            iterable: Iterable[_S] = ...,
            chunksize: Optional[int] = ...) -> List[_T]: ...
    def map_async(self, func: Callable[[_S], _T],
                  iterable: Iterable[_S] = ...,
                  chunksize: Optional[int] = ...,
                  callback: Optional[Callable[[_T], None]] = ...,
                  error_callback: Optional[Callable[[BaseException], None]] = ...) -> AsyncResult[List[_T]]: ...
    def imap(self,
             func: Callable[[_S], _T],
             iterable: Iterable[_S] = ...,
             chunksize: Optional[int] = ...) -> IMapIterator[_T]: ...
    def imap_unordered(self,
                       func: Callable[[_S], _T],
                       iterable: Iterable[_S] = ...,
                       chunksize: Optional[int] = ...) -> IMapIterator[_T]: ...
    def starmap(self,
                func: Callable[..., _T],
                iterable: Iterable[Iterable[Any]] = ...,
                chunksize: Optional[int] = ...) -> List[_T]: ...
    def starmap_async(self,
                      func: Callable[..., _T],
                      iterable: Iterable[Iterable[Any]] = ...,
                      chunksize: Optional[int] = ...,
                      callback: Optional[Callable[[_T], None]] = ...,
                      error_callback: Optional[Callable[[BaseException], None]] = ...) -> AsyncResult[List[_T]]: ...
    def close(self) -> None: ...
    def terminate(self) -> None: ...
    def join(self) -> None: ...
    def __enter__(self: _PT) -> _PT: ...


class ThreadPool(Pool, ContextManager[ThreadPool]):

    def __init__(self, processes: Optional[int] = ...,
                 initializer: Optional[Callable[..., Any]] = ...,
                 initargs: Iterable[Any] = ...) -> None: ...
