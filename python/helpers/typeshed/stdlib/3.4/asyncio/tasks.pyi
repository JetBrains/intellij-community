from typing import (Any, TypeVar, Set, Dict, List, TextIO, Union, Tuple, Generic, Callable,
                    Coroutine, Generator, Iterable, Awaitable, overload, Sequence, Iterator,
                    Optional)
import concurrent.futures
from .events import AbstractEventLoop
from .futures import Future

__all__: List[str]

_T = TypeVar('_T')
_T1 = TypeVar('_T1')
_T2 = TypeVar('_T2')
_T3 = TypeVar('_T3')
_T4 = TypeVar('_T4')
_T5 = TypeVar('_T5')
_FutureT = Union[Future[_T], Generator[Any, None, _T], Awaitable[_T]]

FIRST_EXCEPTION = 'FIRST_EXCEPTION'
FIRST_COMPLETED = 'FIRST_COMPLETED'
ALL_COMPLETED = 'ALL_COMPLETED'

def as_completed(fs: Sequence[_FutureT[_T]], *, loop: AbstractEventLoop = ...,
                 timeout: Optional[float] = ...) -> Iterator[Generator[Any, None, _T]]: ...
def ensure_future(coro_or_future: _FutureT[_T],
                  *, loop: AbstractEventLoop = ...) -> Future[_T]: ...
async = ensure_future
@overload
def gather(coro_or_future1: _FutureT[_T1],
           *, loop: AbstractEventLoop = ..., return_exceptions: bool = False) -> Future[Tuple[_T1]]: ...
@overload
def gather(coro_or_future1: _FutureT[_T1], coro_or_future2: _FutureT[_T2],
           *, loop: AbstractEventLoop = ..., return_exceptions: bool = False) -> Future[Tuple[_T1, _T2]]: ...
@overload
def gather(coro_or_future1: _FutureT[_T1], coro_or_future2: _FutureT[_T2], coro_or_future3: _FutureT[_T3],
           *, loop: AbstractEventLoop = ..., return_exceptions: bool = False) -> Future[Tuple[_T1, _T2, _T3]]: ...
@overload
def gather(coro_or_future1: _FutureT[_T1], coro_or_future2: _FutureT[_T2], coro_or_future3: _FutureT[_T3],
           coro_or_future4: _FutureT[_T4],
           *, loop: AbstractEventLoop = ..., return_exceptions: bool = False) -> Future[Tuple[_T1, _T2, _T3, _T4]]: ...
@overload
def gather(coro_or_future1: _FutureT[_T1], coro_or_future2: _FutureT[_T2], coro_or_future3: _FutureT[_T3],
           coro_or_future4: _FutureT[_T4], coro_or_future5: _FutureT[_T5],
           *, loop: AbstractEventLoop = ..., return_exceptions: bool = False) -> Future[Tuple[_T1, _T2, _T3, _T4, _T5]]: ...
@overload
def gather(coro_or_future1: _FutureT[Any], coro_or_future2: _FutureT[Any], coro_or_future3: _FutureT[Any],
           coro_or_future4: _FutureT[Any], coro_or_future5: _FutureT[Any], coro_or_future6: _FutureT[Any],
           *coros_or_futures: _FutureT[Any],
           loop: AbstractEventLoop = ..., return_exceptions: bool = False) -> Future[Tuple[Any, ...]]: ...
def run_coroutine_threadsafe(coro: _FutureT[_T],
                             loop: AbstractEventLoop) -> concurrent.futures.Future[_T]: ...
def shield(arg: _FutureT[_T], *, loop: AbstractEventLoop = ...) -> Future[_T]: ...
def sleep(delay: float, result: _T = ..., loop: AbstractEventLoop = ...) -> Future[_T]: ...
def wait(fs: Iterable[_FutureT[_T]], *, loop: AbstractEventLoop = ...,
    timeout: float = ...,
         return_when: str = ...) -> Future[Tuple[Set[Future[_T]], Set[Future[_T]]]]: ...
def wait_for(fut: _FutureT[_T], timeout: Optional[float],
             *, loop: AbstractEventLoop = ...) -> Future[_T]: ...

class Task(Future[_T], Generic[_T]):
    _all_tasks = ...  # type: Set[Task]
    _current_tasks = ...  # type: Dict[AbstractEventLoop, Task]
    @classmethod
    def current_task(cls, loop: AbstractEventLoop = ...) -> Task: ...
    @classmethod
    def all_tasks(cls, loop: AbstractEventLoop = ...) -> Set[Task]: ...

    # Can't use a union, see mypy issue  #1873.
    @overload
    def __init__(self, coro: Generator[Any, None, _T],
                 *, loop: AbstractEventLoop = ...) -> None: ...
    @overload
    def __init__(self, coro: Awaitable[_T], *, loop: AbstractEventLoop = ...) -> None: ...

    def __repr__(self) -> str: ...
    def get_stack(self, *, limit: int = ...) -> List[Any]: ...  # return List[stackframe]
    def print_stack(self, *, limit: int = ..., file: TextIO = ...) -> None: ...
    def cancel(self) -> bool: ...
    def _step(self, value: Any = ..., exc: Exception = ...) -> None: ...
    def _wakeup(self, future: Future[Any]) -> None: ...
