import sys
from _typeshed import Incomplete
from typing import Final

if sys.platform == "linux":
    RLIMIT_AS: Final[int]
    RLIMIT_CORE: Final[int]
    RLIMIT_CPU: Final[int]
    RLIMIT_DATA: Final[int]
    RLIMIT_FSIZE: Final[int]
    RLIMIT_LOCKS: Final[int]
    RLIMIT_MEMLOCK: Final[int]
    RLIMIT_MSGQUEUE: Final[int]
    RLIMIT_NICE: Final[int]
    RLIMIT_NOFILE: Final[int]
    RLIMIT_NPROC: Final[int]
    RLIMIT_RSS: Final[int]
    RLIMIT_RTPRIO: Final[int]
    RLIMIT_RTTIME: Final[int]
    RLIMIT_SIGPENDING: Final[int]
    RLIMIT_STACK: Final[int]
    RLIM_INFINITY: Final[int]

def getpagesize() -> int: ...
def getpriority(pid: int, /) -> int: ...
def net_if_addrs() -> list[tuple[str, int, str | None, str | None, str | None, str | None]]: ...
def net_if_flags(nic_name: str, /) -> list[str]: ...
def net_if_is_running(nic_name: str, /) -> bool: ...
def net_if_mtu(nic_name: str, /) -> int: ...

if sys.platform == "darwin":
    AF_LINK: Final[int]
    def net_if_duplex_speed(nic_name: str, /) -> list[int]: ...

def users() -> list[tuple[Incomplete, ...]]: ...
def setpriority(pid: int, priority: int, /) -> None: ...
