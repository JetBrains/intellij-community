import enum
import sys
from _typeshed import FileDescriptorOrPath, Incomplete, StrOrBytesPath, Unused
from collections.abc import Callable

from ._common import sdiskusage

def pid_exists(pid: int) -> bool: ...

# Sync with `signal.Signals`, but with opposite values:
class Negsignal(enum.IntEnum):
    SIGABRT = -6
    SIGFPE = -8
    SIGILL = -4
    SIGINT = -2
    SIGSEGV = -11
    SIGTERM = -15

    if sys.platform == "win32":
        SIGBREAK = -21
        CTRL_C_EVENT = 0
        CTRL_BREAK_EVENT = -1
    else:
        SIGALRM = -14
        SIGBUS = -7
        SIGCHLD = -17
        SIGCONT = -18
        SIGHUP = -1
        SIGIO = -29
        SIGIOT = -6
        SIGKILL = -9
        SIGPIPE = -13
        SIGPROF = -27
        SIGQUIT = -3
        SIGSTOP = -19
        SIGSYS = -31
        SIGTRAP = -5
        SIGTSTP = -20
        SIGTTIN = -21
        SIGTTOU = -22
        SIGURG = -23
        SIGUSR1 = -10
        SIGUSR2 = -12
        SIGVTALRM = -26
        SIGWINCH = -28
        SIGXCPU = -24
        SIGXFSZ = -25
        if sys.platform != "linux":
            SIGEMT = -7
            SIGINFO = -29
        if sys.platform != "darwin":
            SIGCLD = -17
            SIGPOLL = -29
            SIGPWR = -30
            SIGRTMAX = -64
            SIGRTMIN = -34
            if sys.version_info >= (3, 11):
                SIGSTKFLT = -16

def negsig_to_enum(num: int) -> int: ...
def wait_pid(
    pid: int,
    timeout: float | None = None,
    proc_name: str | None = None,
    _waitpid: Unused = ...,
    _timer: Callable[[], float] = ...,
    _min: Callable[..., Incomplete] = ...,
    _sleep: Callable[[float], None] = ...,
    _pid_exists: Callable[[int], bool] = ...,
) -> int | None: ...

if sys.platform == "darwin":
    def disk_usage(path: StrOrBytesPath) -> sdiskusage: ...

else:
    def disk_usage(path: FileDescriptorOrPath) -> sdiskusage: ...

def get_terminal_map() -> dict[int, str]: ...

__all__ = ["pid_exists", "wait_pid", "disk_usage", "get_terminal_map"]
