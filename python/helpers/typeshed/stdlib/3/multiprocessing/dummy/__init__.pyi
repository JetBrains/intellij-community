from typing import Any, Optional, List, Type

import array
import sys
import threading
import weakref

from .connection import Pipe
from threading import Lock, RLock, Semaphore, BoundedSemaphore
from threading import Event, Condition, Barrier
from queue import Queue

JoinableQueue = Queue


class DummyProcess(threading.Thread):
    _children = ...  # type: weakref.WeakKeyDictionary
    _parent = ...  # type: threading.Thread
    _pid = ...  # type: None
    _start_called = ...  # type: int
    exitcode = ...  # type: Optional[int]
    def __init__(self, group=..., target=..., name=..., args=..., kwargs=...) -> None: ...

Process = DummyProcess

class Namespace(object):
    def __init__(self, **kwds) -> None: ...

class Value(object):
    _typecode = ...  # type: Any
    _value = ...  # type: Any
    value = ...  # type: Any
    def __init__(self, typecode, value, lock=...) -> None: ...


def Array(typecode, sequence, lock=...) -> array.array: ...
def Manager() -> Any: ...
def Pool(processes=..., initializer=..., initargs=...) -> Any: ...
def active_children() -> List: ...
def current_process() -> threading.Thread: ...
def freeze_support() -> None: ...
def shutdown() -> None: ...
