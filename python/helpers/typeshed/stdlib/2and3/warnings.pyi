# Stubs for warnings

from typing import Any, Dict, List, NamedTuple, Optional, TextIO, Tuple, Type, Union
from types import ModuleType, TracebackType

def warn(message: Union[str, Warning], category: Optional[Type[Warning]] = ...,
         stacklevel: int = ...) -> None: ...
def warn_explicit(message: Union[str, Warning], category: Type[Warning],
                  filename: str, lineno: int, module: Optional[str] = ...,
                  registry: Optional[Dict[Union[str, Tuple[str, Type[Warning], int]], int]] = ...,
                  module_globals: Optional[Dict[str, Any]] = ...) -> None: ...
def showwarning(message: str, category: Type[Warning], filename: str,
                lineno: int, file: Optional[TextIO] = ...,
                line: Optional[str] = ...) -> None: ...
def formatwarning(message: str, category: Type[Warning], filename: str,
                  lineno: int, line: Optional[str] = ...) -> None: ...
def filterwarnings(action: str, message: str = ...,
                   category: Type[Warning] = ..., module: str = ...,
                   lineno: int = ..., append: bool = ...) -> None: ...
def simplefilter(action: str, category: Type[Warning] = ..., lineno: int = ...,
                 append: bool = ...) -> None: ...
def resetwarnings() -> None: ...

_Record = NamedTuple('_Record',
    [('message', str),
     ('category', Type[Warning]),
     ('filename', str),
     ('lineno', int),
     ('file', Optional[TextIO]),
     ('line', Optional[str])]
)

class catch_warnings:
    def __init__(self, *, record: bool = ...,
                 module: Optional[ModuleType] = ...) -> None: ...
    def __enter__(self) -> Optional[List[_Record]]: ...
    def __exit__(self, exc_type: Optional[Type[BaseException]],
                 exc_val: Optional[Exception],
                 exc_tb: Optional[TracebackType]) -> bool: ...
