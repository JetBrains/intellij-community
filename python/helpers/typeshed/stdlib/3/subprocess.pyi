# Stubs for subprocess

# Based on http://docs.python.org/3.6/library/subprocess.html
import sys
from typing import Sequence, Any, Mapping, Callable, Tuple, IO, Optional, Union, List, Type, Text
from types import TracebackType

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
        args = ...  # type: Any
        returncode = ...  # type: int
        # morally: Optional[_TXT]
        stdout = ...  # type: Any
        stderr = ...  # type: Any
        def __init__(self, args: _CMD,
                     returncode: int,
                     stdout: Optional[_TXT] = ...,
                     stderr: Optional[_TXT] = ...) -> None: ...
        def check_returncode(self) -> None: ...

    if sys.version_info >= (3, 6):
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
                pass_fds: Any = ...,
                *,
                encoding: Optional[str] = ...,
                errors: Optional[str] = ...) -> CompletedProcess: ...
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
if sys.version_info >= (3, 3):
    # 3.3 added timeout
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
else:
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
             pass_fds: Any = ...) -> int: ...

# Same args as Popen.__init__
if sys.version_info >= (3, 3):
    # 3.3 added timeout
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
else:
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
                   pass_fds: Any = ...) -> int: ...

if sys.version_info >= (3, 6):
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
elif sys.version_info >= (3, 4):
    # 3.4 added input
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
elif sys.version_info >= (3, 3):
    # 3.3 added timeout
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
                     ) -> Any: ...  # morally: -> _TXT
else:
    # Same args as Popen.__init__, except for stdout
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
                     ) -> Any: ...  # morally: -> _TXT


PIPE = ...  # type: int
STDOUT = ...  # type: int
if sys.version_info >= (3, 3):
    DEVNULL = ...  # type: int
    class SubprocessError(Exception): ...
    class TimeoutExpired(SubprocessError): ...


class CalledProcessError(Exception):
    returncode = 0
    # morally: _CMD
    cmd = ...  # type: Any
    # morally: Optional[_TXT]
    output = ...  # type: Any

    if sys.version_info >= (3, 5):
        # morally: Optional[_TXT]
        stdout = ...  # type: Any
        stderr = ...  # type: Any

    def __init__(self,
                 returncode: int,
                 cmd: _CMD,
                 output: Optional[_TXT] = ...,
                 stderr: Optional[_TXT] = ...) -> None: ...

class Popen:
    if sys.version_info >= (3, 3):
        args = ...  # type: _CMD
    stdin = ...  # type: IO[Any]
    stdout = ...  # type: IO[Any]
    stderr = ...  # type: IO[Any]
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
    if sys.version_info >= (3, 3):
        # 3.3 added timeout
        def wait(self, timeout: Optional[float] = ...) -> int: ...
    else:
        def wait(self) ->int: ...
    # Return str/bytes
    if sys.version_info >= (3, 3):
        def communicate(self,
                        input: Optional[_TXT] = ...,
                        timeout: Optional[float] = ...,
                        # morally: -> Tuple[Optional[_TXT], Optional[_TXT]]
                        ) -> Tuple[Any, Any]: ...
    else:
        def communicate(self,
                        input: Optional[_TXT] = ...,
                        # morally: -> Tuple[Optional[_TXT], Optional[_TXT]]
                        ) -> Tuple[Any, Any]: ...
    def send_signal(self, signal: int) -> None: ...
    def terminate(self) -> None: ...
    def kill(self) -> None: ...
    def __enter__(self) -> 'Popen': ...
    def __exit__(self, type: Optional[Type[BaseException]], value: Optional[BaseException], traceback: Optional[TracebackType]) -> bool: ...

# The result really is always a str.
def getstatusoutput(cmd: _TXT) -> Tuple[int, str]: ...
def getoutput(cmd: _TXT) -> str: ...

# Windows-only: STARTUPINFO etc.
