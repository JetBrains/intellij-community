import threading
from types import TracebackType
from typing import Iterator, Optional, Type
from typing_extensions import Literal, Protocol

__version__: str

class _Stream(Protocol):
    def isatty(self) -> bool: ...
    def flush(self) -> None: ...
    def write(self, s: str) -> int: ...

class Spinner(object):
    spinner_cycle: Iterator[str]
    disable: bool
    beep: bool
    force: bool
    stream: _Stream
    stop_running: Optional[threading.Event]
    spin_thread: Optional[threading.Thread]
    def __init__(
        self,
        beep: bool,
        disable: bool,
        force: bool,
        stream: _Stream,
    ) -> None: ...
    def start(self) -> None: ...
    def stop(self) -> None: ...
    def init_spin(self) -> None: ...
    def __enter__(self) -> Spinner: ...
    def __exit__(
        self,
        exc_type: Optional[Type[BaseException]],
        exc_val: Optional[BaseException],
        exc_tb: Optional[TracebackType],
    ) -> Literal[False]: ...

def spinner(beep: bool, disable: bool, force: bool, stream: _Stream) -> Spinner: ...
