"""Stubs for the 'select' module."""

from typing import Any, Optional, Tuple, Iterable, List

EPOLLERR = ...  # type: int
EPOLLET = ...  # type: int
EPOLLHUP = ...  # type: int
EPOLLIN = ...  # type: int
EPOLLMSG = ...  # type: int
EPOLLONESHOT = ...  # type: int
EPOLLOUT = ...  # type: int
EPOLLPRI = ...  # type: int
EPOLLRDBAND = ...  # type: int
EPOLLRDNORM = ...  # type: int
EPOLLWRBAND = ...  # type: int
EPOLLWRNORM = ...  # type: int
EPOLL_RDHUP = ...  # type: int
KQ_EV_ADD = ...  # type: int
KQ_EV_CLEAR = ...  # type: int
KQ_EV_DELETE = ...  # type: int
KQ_EV_DISABLE = ...  # type: int
KQ_EV_ENABLE = ...  # type: int
KQ_EV_EOF = ...  # type: int
KQ_EV_ERROR = ...  # type: int
KQ_EV_FLAG1 = ...  # type: int
KQ_EV_ONESHOT = ...  # type: int
KQ_EV_SYSFLAGS = ...  # type: int
KQ_FILTER_AIO = ...  # type: int
KQ_FILTER_NETDEV = ...  # type: int
KQ_FILTER_PROC = ...  # type: int
KQ_FILTER_READ = ...  # type: int
KQ_FILTER_SIGNAL = ...  # type: int
KQ_FILTER_TIMER = ...  # type: int
KQ_FILTER_VNODE = ...  # type: int
KQ_FILTER_WRITE = ...  # type: int
KQ_NOTE_ATTRIB = ...  # type: int
KQ_NOTE_CHILD = ...  # type: int
KQ_NOTE_DELETE = ...  # type: int
KQ_NOTE_EXEC = ...  # type: int
KQ_NOTE_EXIT = ...  # type: int
KQ_NOTE_EXTEND = ...  # type: int
KQ_NOTE_FORK = ...  # type: int
KQ_NOTE_LINK = ...  # type: int
KQ_NOTE_LINKDOWN = ...  # type: int
KQ_NOTE_LINKINV = ...  # type: int
KQ_NOTE_LINKUP = ...  # type: int
KQ_NOTE_LOWAT = ...  # type: int
KQ_NOTE_PCTRLMASK = ...  # type: int
KQ_NOTE_PDATAMASK = ...  # type: int
KQ_NOTE_RENAME = ...  # type: int
KQ_NOTE_REVOKE = ...  # type: int
KQ_NOTE_TRACK = ...  # type: int
KQ_NOTE_TRACKERR = ...  # type: int
KQ_NOTE_WRITE = ...  # type: int
PIPE_BUF = ...  # type: int
POLLERR = ...  # type: int
POLLHUP = ...  # type: int
POLLIN = ...  # type: int
POLLMSG = ...  # type: int
POLLNVAL = ...  # type: int
POLLOUT = ...  # type: int
POLLPRI = ...  # type: int
POLLRDBAND = ...  # type: int
POLLRDNORM = ...  # type: int
POLLWRBAND = ...  # type: int
POLLWRNORM = ...  # type: int

def poll() -> epoll: ...
def select(rlist, wlist, xlist, timeout: float = None) -> Tuple[List, List, List]: ...

class error(Exception): ...

class kevent(object):
    data = ...  # type: Any
    fflags = ...  # type: int
    filter = ...  # type: int
    flags = ...  # type: int
    ident = ...  # type: Any
    udata = ...  # type: Any
    def __init__(self, *args, **kwargs) -> None: ...

class kqueue(object):
    closed = ...  # type: bool
    def __init__(self) -> None: ...
    def close(self) -> None: ...
    def control(self, changelist: Optional[Iterable[kevent]], max_events: int, timeout: int = ...) -> List[kevent]: ...
    def fileno(self) -> int: ...
    @classmethod
    def fromfd(cls, fd: int) -> kqueue: ...

class epoll(object):
    def __init__(self, sizehint: int = ...) -> None: ...
    def close(self) -> None: ...
    def fileno(self) -> int: ...
    def register(self, fd: int, eventmask: int = ...) -> None: ...
    def modify(self, fd: int, eventmask: int) -> None: ...
    def unregister(self, fd: int) -> None: ...
    def poll(self, timeout: float = ..., maxevents: int = ...) -> Any: ...
    @classmethod
    def fromfd(cls, fd: int) -> epoll: ...
