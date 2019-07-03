# Stubs for subprocess

# Based on http://docs.python.org/3.6/library/subprocess.html
import sys
from typing import Sequence, Any, Mapping, Callable, Tuple, IO, Optional, Union, List, Type, Text
from types import TracebackType

# We prefer to annotate inputs to methods (eg subprocess.check_call) with these
# union types. However, outputs (eg check_call return) and class attributes
# (eg TimeoutError.cmd) we prefer to annotate with Any, so the caller does not
# have to use an assertion to confirm which type.
#
# For example:
#
# try:
#    x = subprocess.check_output(["ls", "-l"])
#    reveal_type(x)  # Any, but morally is _TXT
# except TimeoutError as e:
#    reveal_type(e.cmd)  # Any, but morally is _CMD
_FILE = Union[None, int, IO[Any]]
_TXT = Union[bytes, Text]
if sys.version_info >= (3, 6):
    from builtins import _PathLike
    _PATH = Union[bytes, Text, _PathLike]
else:
    _PATH = Union[bytes, Text]
# Python 3.6 does't support _CMD being a single PathLike.
# See: https://bugs.python.org/issue31961
_CMD = Union[_TXT, Sequence[_PATH]]
_ENV = Union[Mapping[bytes, _TXT], Mapping[Text, _TXT]]

if sys.version_info >= (3, 5):
    class CompletedProcess:
        # morally: _CMD
        args: Any
        returncode: int
        # morally: Optional[_TXT]
        stdout: Any
        stderr: Any
        def __init__(self, args: _CMD,
                     returncode: int,
                     stdout: Optional[_TXT] = ...,
                     stderr: Optional[_TXT] = ...) -> None: ...
        def check_returncode(self) -> None: ...

    if sys.version_info >= (3, 7):
        # Nearly the same args as for 3.6, except for capture_output and text
        def run(args: _CMD,
                bufsize: int = ...,
                executable: _PATH = ...,
                stdin: _FILE = ...,
                stdout: _FILE = ...,
                stderr: _FILE = ...,
                preexec_fn: Callable[[], Any] = ...,
                close_fds: bool = ...,
                shell: bool = ...,
                cwd: Optional[_PATH] = ...,
                env: Optional[_ENV] = ...,
                universal_newlines: bool = ...,
                startupinfo: Any = ...,
                creationflags: int = ...,
                restore_signals: bool = ...,
                start_new_session: bool = ...,
                pass_fds: Any = ...,
                *,
                capture_output: bool = ...,
                check: bool = ...,
                encoding: Optional[str] = ...,
                errors: Optional[str] = ...,
                input: Optional[_TXT] = ...,
                text: Optional[bool] = ...,
                timeout: Optional[float] = ...) -> CompletedProcess: ...
    elif sys.version_info >= (3, 6):
        # Nearly same args as Popen.__init__ except for timeout, input, and check
        def run(args: _CMD,
                bufsize: int = ...,
                executable: _PATH = ...,
                stdin: _FILE = ...,
                stdout: _FILE = ...,
                stderr: _FILE = ...,
                preexec_fn: Callable[[], Any] = ...,
                close_fds: bool = ...,
                shell: bool = ...,
                cwd: Optional[_PATH] = ...,
                env: Optional[_ENV] = ...,
                universal_newlines: bool = ...,
                startupinfo: Any = ...,
                creationflags: int = ...,
                restore_signals: bool = ...,
                start_new_session: bool = ...,
                pass_fds: Any = ...,
                *,
                check: bool = ...,
                encoding: Optional[str] = ...,
                errors: Optional[str] = ...,
                input: Optional[_TXT] = ...,
                timeout: Optional[float] = ...) -> CompletedProcess: ...
    else:
        # Nearly same args as Popen.__init__ except for timeout, input, and check
        def run(args: _CMD,
                timeout: Optional[float] = ...,
                input: Optional[_TXT] = ...,
                check: bool = ...,
                bufsize: int = ...,
                executable: _PATH = ...,
                stdin: _FILE = ...,
                stdout: _FILE = ...,
                stderr: _FILE = ...,
                preexec_fn: Callable[[], Any] = ...,
                close_fds: bool = ...,
                shell: bool = ...,
                cwd: Optional[_PATH] = ...,
                env: Optional[_ENV] = ...,
                universal_newlines: bool = ...,
                startupinfo: Any = ...,
                creationflags: int = ...,
                restore_signals: bool = ...,
                start_new_session: bool = ...,
                pass_fds: Any = ...) -> CompletedProcess: ...

# Same args as Popen.__init__
def call(args: _CMD,
         bufsize: int = ...,
         executable: _PATH = ...,
         stdin: _FILE = ...,
         stdout: _FILE = ...,
         stderr: _FILE = ...,
         preexec_fn: Callable[[], Any] = ...,
         close_fds: bool = ...,
         shell: bool = ...,
         cwd: Optional[_PATH] = ...,
         env: Optional[_ENV] = ...,
         universal_newlines: bool = ...,
         startupinfo: Any = ...,
         creationflags: int = ...,
         restore_signals: bool = ...,
         start_new_session: bool = ...,
         pass_fds: Any = ...,
         timeout: float = ...) -> int: ...

# Same args as Popen.__init__
def check_call(args: _CMD,
               bufsize: int = ...,
               executable: _PATH = ...,
               stdin: _FILE = ...,
               stdout: _FILE = ...,
               stderr: _FILE = ...,
               preexec_fn: Callable[[], Any] = ...,
               close_fds: bool = ...,
               shell: bool = ...,
               cwd: Optional[_PATH] = ...,
               env: Optional[_ENV] = ...,
               universal_newlines: bool = ...,
               startupinfo: Any = ...,
               creationflags: int = ...,
               restore_signals: bool = ...,
               start_new_session: bool = ...,
               pass_fds: Any = ...,
               timeout: float = ...) -> int: ...

if sys.version_info >= (3, 7):
    # 3.7 added text
    def check_output(args: _CMD,
                     bufsize: int = ...,
                     executable: _PATH = ...,
                     stdin: _FILE = ...,
                     stderr: _FILE = ...,
                     preexec_fn: Callable[[], Any] = ...,
                     close_fds: bool = ...,
                     shell: bool = ...,
                     cwd: Optional[_PATH] = ...,
                     env: Optional[_ENV] = ...,
                     universal_newlines: bool = ...,
                     startupinfo: Any = ...,
                     creationflags: int = ...,
                     restore_signals: bool = ...,
                     start_new_session: bool = ...,
                     pass_fds: Any = ...,
                     *,
                     timeout: float = ...,
                     input: _TXT = ...,
                     encoding: Optional[str] = ...,
                     errors: Optional[str] = ...,
                     text: Optional[bool] = ...,
                     ) -> Any: ...  # morally: -> _TXT
elif sys.version_info >= (3, 6):
    # 3.6 added encoding and errors
    def check_output(args: _CMD,
                     bufsize: int = ...,
                     executable: _PATH = ...,
                     stdin: _FILE = ...,
                     stderr: _FILE = ...,
                     preexec_fn: Callable[[], Any] = ...,
                     close_fds: bool = ...,
                     shell: bool = ...,
                     cwd: Optional[_PATH] = ...,
                     env: Optional[_ENV] = ...,
                     universal_newlines: bool = ...,
                     startupinfo: Any = ...,
                     creationflags: int = ...,
                     restore_signals: bool = ...,
                     start_new_session: bool = ...,
                     pass_fds: Any = ...,
                     *,
                     timeout: float = ...,
                     input: _TXT = ...,
                     encoding: Optional[str] = ...,
                     errors: Optional[str] = ...,
                     ) -> Any: ...  # morally: -> _TXT
else:
    def check_output(args: _CMD,
                     bufsize: int = ...,
                     executable: _PATH = ...,
                     stdin: _FILE = ...,
                     stderr: _FILE = ...,
                     preexec_fn: Callable[[], Any] = ...,
                     close_fds: bool = ...,
                     shell: bool = ...,
                     cwd: Optional[_PATH] = ...,
                     env: Optional[_ENV] = ...,
                     universal_newlines: bool = ...,
                     startupinfo: Any = ...,
                     creationflags: int = ...,
                     restore_signals: bool = ...,
                     start_new_session: bool = ...,
                     pass_fds: Any = ...,
                     timeout: float = ...,
                     input: _TXT = ...,
                     ) -> Any: ...  # morally: -> _TXT


PIPE: int
STDOUT: int
DEVNULL: int
class SubprocessError(Exception): ...
class TimeoutExpired(SubprocessError):
    def __init__(self, cmd: _CMD, timeout: float, output: Optional[_TXT] = ..., stderr: Optional[_TXT] = ...) -> None: ...
    # morally: _CMD
    cmd: Any
    timeout: float
    # morally: Optional[_TXT]
    output: Any
    stdout: Any
    stderr: Any


class CalledProcessError(Exception):
    returncode = 0
    # morally: _CMD
    cmd: Any
    # morally: Optional[_TXT]
    output: Any

    if sys.version_info >= (3, 5):
        # morally: Optional[_TXT]
        stdout: Any
        stderr: Any

    def __init__(self,
                 returncode: int,
                 cmd: _CMD,
                 output: Optional[_TXT] = ...,
                 stderr: Optional[_TXT] = ...) -> None: ...

class Popen:
    args: _CMD
    stdin: IO[Any]
    stdout: IO[Any]
    stderr: IO[Any]
    pid = 0
    returncode = 0

    if sys.version_info >= (3, 6):
        def __init__(self,
                     args: _CMD,
                     bufsize: int = ...,
                     executable: Optional[_PATH] = ...,
                     stdin: Optional[_FILE] = ...,
                     stdout: Optional[_FILE] = ...,
                     stderr: Optional[_FILE] = ...,
                     preexec_fn: Optional[Callable[[], Any]] = ...,
                     close_fds: bool = ...,
                     shell: bool = ...,
                     cwd: Optional[_PATH] = ...,
                     env: Optional[_ENV] = ...,
                     universal_newlines: bool = ...,
                     startupinfo: Optional[Any] = ...,
                     creationflags: int = ...,
                     restore_signals: bool = ...,
                     start_new_session: bool = ...,
                     pass_fds: Any = ...,
                     *,
                     encoding: Optional[str] = ...,
                     errors: Optional[str] = ...) -> None: ...
    else:
        def __init__(self,
                     args: _CMD,
                     bufsize: int = ...,
                     executable: Optional[_PATH] = ...,
                     stdin: Optional[_FILE] = ...,
                     stdout: Optional[_FILE] = ...,
                     stderr: Optional[_FILE] = ...,
                     preexec_fn: Optional[Callable[[], Any]] = ...,
                     close_fds: bool = ...,
                     shell: bool = ...,
                     cwd: Optional[_PATH] = ...,
                     env: Optional[_ENV] = ...,
                     universal_newlines: bool = ...,
                     startupinfo: Optional[Any] = ...,
                     creationflags: int = ...,
                     restore_signals: bool = ...,
                     start_new_session: bool = ...,
                     pass_fds: Any = ...) -> None: ...

    def poll(self) -> int: ...
    def wait(self, timeout: Optional[float] = ...) -> int: ...
    # Return str/bytes
    def communicate(self,
                    input: Optional[_TXT] = ...,
                    timeout: Optional[float] = ...,
                    # morally: -> Tuple[Optional[_TXT], Optional[_TXT]]
                    ) -> Tuple[Any, Any]: ...
    def send_signal(self, signal: int) -> None: ...
    def terminate(self) -> None: ...
    def kill(self) -> None: ...
    def __enter__(self) -> Popen: ...
    def __exit__(self, type: Optional[Type[BaseException]], value: Optional[BaseException], traceback: Optional[TracebackType]) -> bool: ...

# The result really is always a str.
def getstatusoutput(cmd: _TXT) -> Tuple[int, str]: ...
def getoutput(cmd: _TXT) -> str: ...

def list2cmdline(seq: Sequence[str]) -> str: ...  # undocumented

if sys.platform == 'win32':
    class STARTUPINFO:
        if sys.version_info >= (3, 7):
            def __init__(self, *, dwFlags: int = ..., hStdInput: Optional[Any] = ..., hStdOutput: Optional[Any] = ..., hStdError: Optional[Any] = ..., wShowWindow: int = ..., lpAttributeList: Optional[Mapping[str, Any]] = ...) -> None: ...
        dwFlags: int
        hStdInput: Optional[Any]
        hStdOutput: Optional[Any]
        hStdError: Optional[Any]
        wShowWindow: int
        if sys.version_info >= (3, 7):
            lpAttributeList: Mapping[str, Any]

    STD_INPUT_HANDLE: Any
    STD_OUTPUT_HANDLE: Any
    STD_ERROR_HANDLE: Any
    SW_HIDE: int
    STARTF_USESTDHANDLES: int
    STARTF_USESHOWWINDOW: int
    CREATE_NEW_CONSOLE: int
    CREATE_NEW_PROCESS_GROUP: int
    if sys.version_info >= (3, 7):
        ABOVE_NORMAL_PRIORITY_CLASS: int
        BELOW_NORMAL_PRIORITY_CLASS: int
        HIGH_PRIORITY_CLASS: int
        IDLE_PRIORITY_CLASS: int
        NORMAL_PRIORITY_CLASS: int
        REALTIME_PRIORITY_CLASS: int
        CREATE_NO_WINDOW: int
        DETACHED_PROCESS: int
        CREATE_DEFAULT_ERROR_MODE: int
        CREATE_BREAKAWAY_FROM_JOB: int
