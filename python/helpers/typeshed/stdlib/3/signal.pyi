"""Stub file for the 'signal' module."""

import sys
from enum import IntEnum
from typing import Any, Callable, List, Tuple, Dict, Generic, Union, Optional, Iterable, Set
from types import FrameType

class ItimerError(IOError): ...

ITIMER_PROF = ...  # type: int
ITIMER_REAL = ...  # type: int
ITIMER_VIRTUAL = ...  # type: int

NSIG = ...  # type: int

if sys.version_info >= (3, 5):
    class Signals(IntEnum):
        SIGABRT = ...
        SIGALRM = ...
        SIGBUS = ...
        SIGCHLD = ...
        SIGCLD = ...
        SIGCONT = ...
        SIGFPE = ...
        SIGHUP = ...
        SIGILL = ...
        SIGINT = ...
        SIGIO = ...
        SIGIOT = ...
        SIGKILL = ...
        SIGPIPE = ...
        SIGPOLL = ...
        SIGPROF = ...
        SIGPWR = ...
        SIGQUIT = ...
        SIGRTMAX = ...
        SIGRTMIN = ...
        SIGSEGV = ...
        SIGSTOP = ...
        SIGSYS = ...
        SIGTERM = ...
        SIGTRAP = ...
        SIGTSTP = ...
        SIGTTIN = ...
        SIGTTOU = ...
        SIGURG = ...
        SIGUSR1 = ...
        SIGUSR2 = ...
        SIGVTALRM = ...
        SIGWINCH = ...
        SIGXCPU = ...
        SIGXFSZ = ...

    class Handlers(IntEnum):
        SIG_DFL = ...
        SIG_IGN = ...

    SIG_DFL = Handlers.SIG_DFL
    SIG_IGN = Handlers.SIG_IGN

    class Sigmasks(IntEnum):
        SIG_BLOCK = ...
        SIG_UNBLOCK = ...
        SIG_SETMASK = ...

    SIG_BLOCK = Sigmasks.SIG_BLOCK
    SIG_UNBLOCK = Sigmasks.SIG_UNBLOCK
    SIG_SETMASK = Sigmasks.SIG_SETMASK

    _SIG = Signals
    _SIGNUM = Union[int, Signals]
    _HANDLER = Union[Callable[[Signals, FrameType], None], int, Handlers, None]
else:
    SIG_DFL = ...  # type: int
    SIG_IGN = ...  # type: int

    SIG_BLOCK = ...  # type: int
    SIG_UNBLOCK = ...  # type: int
    SIG_SETMASK = ...  # type: int

    _SIG = int
    _SIGNUM = int
    _HANDLER = Union[Callable[[int, FrameType], None], int, None]

SIGABRT = ...  # type: _SIG
SIGALRM = ...  # type: _SIG
SIGBUS = ...  # type: _SIG
SIGCHLD = ...  # type: _SIG
SIGCLD = ...  # type: _SIG
SIGCONT = ...  # type: _SIG
SIGFPE = ...  # type: _SIG
SIGHUP = ...  # type: _SIG
SIGILL = ...  # type: _SIG
SIGINT = ...  # type: _SIG
SIGIO = ...  # type: _SIG
SIGIOT = ...  # type: _SIG
SIGKILL = ...  # type: _SIG
SIGPIPE = ...  # type: _SIG
SIGPOLL = ...  # type: _SIG
SIGPROF = ...  # type: _SIG
SIGPWR = ...  # type: _SIG
SIGQUIT = ...  # type: _SIG
SIGRTMAX = ...  # type: _SIG
SIGRTMIN = ...  # type: _SIG
SIGSEGV = ...  # type: _SIG
SIGSTOP = ...  # type: _SIG
SIGSYS = ...  # type: _SIG
SIGTERM = ...  # type: _SIG
SIGTRAP = ...  # type: _SIG
SIGTSTP = ...  # type: _SIG
SIGTTIN = ...  # type: _SIG
SIGTTOU = ...  # type: _SIG
SIGURG = ...  # type: _SIG
SIGUSR1 = ...  # type: _SIG
SIGUSR2 = ...  # type: _SIG
SIGVTALRM = ...  # type: _SIG
SIGWINCH = ...  # type: _SIG
SIGXCPU = ...  # type: _SIG
SIGXFSZ = ...  # type: _SIG

CTRL_C_EVENT = 0  # Windows
CTRL_BREAK_EVENT = 0  # Windows

class struct_siginfo(Tuple[int, int, int, int, int, int, int]):
    def __init__(self, sequence: Iterable[int]) -> None: ...
    @property
    def si_signo(self) -> int: ...
    @property
    def si_code(self) -> int: ...
    @property
    def si_errno(self) -> int: ...
    @property
    def si_pid(self) -> int: ...
    @property
    def si_uid(self) -> int: ...
    @property
    def si_status(self) -> int: ...
    @property
    def si_band(self) -> int: ...

def alarm(time: int) -> int: ...

def default_int_handler(signum: int, frame: FrameType) -> None:
    raise KeyboardInterrupt()

def getitimer(which: int) -> Tuple[float, float]: ...

def getsignal(signalnum: _SIGNUM) -> _HANDLER:
    raise ValueError()

def pause() -> None: ...

def pthread_kill(thread_id: int, signum: int) -> None:
    raise OSError()

def pthread_sigmask(how: int, mask: Iterable[int]) -> Set[_SIGNUM]:
    raise OSError()

def set_wakeup_fd(fd: int) -> int: ...

def setitimer(which: int, seconds: float, interval: float = ...) -> Tuple[float, float]: ...

def siginterrupt(signalnum: int, flag: bool) -> None:
    raise OSError()

def signal(signalnum: _SIGNUM, handler: _HANDLER) -> _HANDLER:
    raise OSError()

def sigpending() -> Any:
    raise OSError()

def sigtimedwait(sigset: Iterable[int], timeout: float) -> Optional[struct_siginfo]:
    raise OSError()
    raise ValueError()

def sigwait(sigset: Iterable[int]) -> _SIGNUM:
    raise OSError()

def sigwaitinfo(sigset: Iterable[int]) -> struct_siginfo:
    raise OSError()
