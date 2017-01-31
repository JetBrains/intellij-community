# Stubs for subprocess

# Based on http://docs.python.org/2/library/subprocess.html and Python 3 stub

from typing import Sequence, Any, AnyStr, Mapping, Callable, Tuple, IO, Union, Optional

_FILE = Union[int, IO[Any]]

# Same args as Popen.__init__
def call(args: Union[str, Sequence[str]],
         bufsize: int = ...,
         executable: str = ...,
         stdin: _FILE = ...,
         stdout: _FILE = ...,
         stderr: _FILE = ...,
         preexec_fn: Callable[[], Any] = ...,
         close_fds: bool = ...,
         shell: bool = ...,
         cwd: str = ...,
         env: Mapping[str, str] = ...,
         universal_newlines: bool = ...,
         startupinfo: Any = ...,
         creationflags: int = ...) -> int: ...

def check_call(args: Union[str, Sequence[str]],
               bufsize: int = ...,
               executable: str = ...,
               stdin: _FILE = ...,
               stdout: _FILE = ...,
               stderr: _FILE = ...,
               preexec_fn: Callable[[], Any] = ...,
               close_fds: bool = ...,
               shell: bool = ...,
               cwd: str = ...,
               env: Mapping[str, str] = ...,
               universal_newlines: bool = ...,
               startupinfo: Any = ...,
               creationflags: int = ...) -> int: ...

# Same args as Popen.__init__ except for stdout
def check_output(args: Union[str, Sequence[str]],
                 bufsize: int = ...,
                 executable: str = ...,
                 stdin: _FILE = ...,
                 stderr: _FILE = ...,
                 preexec_fn: Callable[[], Any] = ...,
                 close_fds: bool = ...,
                 shell: bool = ...,
                 cwd: str = ...,
                 env: Mapping[str, str] = ...,
                 universal_newlines: bool = ...,
                 startupinfo: Any = ...,
                 creationflags: int = ...) -> str: ...

PIPE = ...  # type: int
STDOUT = ...  # type: int

class CalledProcessError(Exception):
    returncode = 0
    cmd = ...  # type: str
    output = ...  # type: str  # May be None

    def __init__(self, returncode: int, cmd: str, output: Optional[str] = ...) -> None: ...

class Popen:
    stdin = ...  # type: Optional[IO[Any]]
    stdout = ...  # type: Optional[IO[Any]]
    stderr = ...  # type: Optional[IO[Any]]
    pid = 0
    returncode = 0

    def __init__(self,
                 args: Union[str, Sequence[str]],
                 bufsize: int = ...,
                 executable: Optional[str] = ...,
                 stdin: Optional[_FILE] = ...,
                 stdout: Optional[_FILE] = ...,
                 stderr: Optional[_FILE] = ...,
                 preexec_fn: Optional[Callable[[], Any]] = ...,
                 close_fds: bool = ...,
                 shell: bool = ...,
                 cwd: Optional[str] = ...,
                 env: Optional[Mapping[str, str]] = ...,
                 universal_newlines: bool = ...,
                 startupinfo: Optional[Any] = ...,
                 creationflags: int = ...) -> None: ...

    def poll(self) -> int: ...
    def wait(self) -> int: ...
    def communicate(self, input: Optional[AnyStr] = ...) -> Tuple[Optional[bytes], Optional[bytes]]: ...
    def send_signal(self, signal: int) -> None: ...
    def terminate(self) -> None: ...
    def kill(self) -> None: ...
    def __enter__(self) -> 'Popen': ...
    def __exit__(self, type, value, traceback) -> bool: ...

def getstatusoutput(cmd: str) -> Tuple[int, str]: ...
def getoutput(cmd: str) -> str: ...

# Windows-only: STARTUPINFO etc.

STD_INPUT_HANDLE = ...  # type: Any
STD_OUTPUT_HANDLE = ...  # type: Any
STD_ERROR_HANDLE = ...  # type: Any
SW_HIDE = ...  # type: Any
STARTF_USESTDHANDLES = ...  # type: Any
STARTF_USESHOWWINDOW = ...  # type: Any
CREATE_NEW_CONSOLE = ...  # type: Any
CREATE_NEW_PROCESS_GROUP = ...  # type: Any
