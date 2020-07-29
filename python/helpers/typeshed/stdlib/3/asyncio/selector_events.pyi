import selectors
import sys
from socket import socket
from typing import Any, Optional, Union

from . import base_events, events

if sys.version_info >= (3, 7):
    from os import PathLike
    _Path = Union[str, PathLike[str]]
else:
    _Path = str

class BaseSelectorEventLoop(base_events.BaseEventLoop):
    def __init__(self, selector: Optional[selectors.BaseSelector] = ...) -> None: ...
