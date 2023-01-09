import array
import threading
import weakref
from collections.abc import Callable, Iterable, Mapping, Sequence
from queue import Queue as Queue
from typing import Any
from typing_extensions import Literal

from .connection import Pipe as Pipe

__all__ = [
    "Process",
    "current_process",
    "active_children",
    "freeze_support",
    "Lock",
    "RLock",
    "Semaphore",
    "BoundedSemaphore",
    "Condition",
    "Event",
    "Barrier",
    "Queue",
    "Manager",
    "Pipe",
    "Pool",
    "JoinableQueue",
]

JoinableQueue = Queue
Barrier = threading.Barrier
BoundedSemaphore = threading.BoundedSemaphore
Condition = threading.Condition
Event = threading.Event
Lock = threading.Lock
RLock = threading.RLock
Semaphore = threading.Semaphore

class DummyProcess(threading.Thread):
    _children: weakref.WeakKeyDictionary[Any, Any]
    _parent: threading.Thread
    _pid: None
    _start_called: int
    @property
    def exitcode(self) -> Literal[0] | None: ...
    def __init__(
        self,
        group: Any = ...,
        target: Callable[..., Any] | None = ...,
        name: str | None = ...,
        args: Iterable[Any] = ...,
        kwargs: Mapping[str, Any] = ...,
    ) -> None: ...

Process = DummyProcess

class Namespace:
    def __init__(self, **kwds: Any) -> None: ...
    def __getattr__(self, __name: str) -> Any: ...
    def __setattr__(self, __name: str, __value: Any) -> None: ...

class Value:
    _typecode: Any
    _value: Any
    value: Any
    def __init__(self, typecode: Any, value: Any, lock: Any = ...) -> None: ...

def Array(typecode: Any, sequence: Sequence[Any], lock: Any = ...) -> array.array[Any]: ...
def Manager() -> Any: ...
def Pool(processes: int | None = ..., initializer: Callable[..., Any] | None = ..., initargs: Iterable[Any] = ...) -> Any: ...
def active_children() -> list[Any]: ...
def current_process() -> threading.Thread: ...
def freeze_support() -> None: ...
def shutdown() -> None: ...
