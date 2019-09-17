from typing import Any, Callable, Optional, Tuple, TypeVar, Generic
from ._base import Executor, Future
import sys

if sys.version_info >= (3, 7):
    from ._base import BrokenExecutor
    class BrokenThreadPool(BrokenExecutor): ...

_S = TypeVar('_S')

class ThreadPoolExecutor(Executor):
    if sys.version_info >= (3, 7):
        def __init__(self, max_workers: Optional[int] = ...,
                     thread_name_prefix: str = ...,
                     initializer: Optional[Callable[..., None]] = ...,
                     initargs: Tuple[Any, ...] = ...) -> None: ...
    elif sys.version_info >= (3, 6) or sys.version_info < (3,):
        def __init__(self, max_workers: Optional[int] = ...,
                     thread_name_prefix: str = ...) -> None: ...
    else:
        def __init__(self, max_workers: Optional[int] = ...) -> None: ...


class _WorkItem(Generic[_S]):
    future: Future
    fn: Callable[[Future[_S]], Any]
    args: Any
    kwargs: Any
    def __init__(self, future: Future, fn: Callable[[Future[_S]], Any], args: Any,
                 kwargs: Any) -> None: ...
    def run(self) -> None: ...
