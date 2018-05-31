from typing import Any, Iterable, List, Optional, Union, TextIO, Tuple, TypeVar

_T = TypeVar('_T')


class Popen3:
    sts = ...  # type: int
    cmd = ...  # type: Iterable
    pid = ...  # type: int
    tochild = ...  # type: TextIO
    fromchild = ...  # type: TextIO
    childerr = ...  # type: Optional[TextIO]
    def __init__(self, cmd: Iterable = ..., capturestderr: bool = ..., bufsize: int = ...) -> None: ...
    def __del__(self) -> None: ...
    def poll(self, _deadstate: _T = ...) -> Union[int, _T]: ...
    def wait(self) -> int: ...

class Popen4(Popen3):
    childerr = ...  # type: None
    cmd = ...  # type: Iterable
    pid = ...  # type: int
    tochild = ...  # type: TextIO
    fromchild = ...  # type: TextIO
    def __init__(self, cmd: Iterable = ..., bufsize: int = ...) -> None: ...

def popen2(cmd: Iterable = ..., bufsize: int = ..., mode: str = ...) -> Tuple[TextIO, TextIO]: ...
def popen3(cmd: Iterable = ..., bufsize: int = ..., mode: str = ...) -> Tuple[TextIO, TextIO, TextIO]: ...
def popen4(cmd: Iterable = ..., bufsize: int = ..., mode: str = ...) -> Tuple[TextIO, TextIO]: ...
