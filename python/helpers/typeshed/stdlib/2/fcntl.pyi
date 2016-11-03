from typing import Any, Union
import io

FASYNC = ...  # type: int
FD_CLOEXEC = ...  # type: int

DN_ACCESS = ...  # type: int
DN_ATTRIB = ...  # type: int
DN_CREATE = ...  # type: int
DN_DELETE = ...  # type: int
DN_MODIFY = ...  # type: int
DN_MULTISHOT = ...  # type: int
DN_RENAME = ...  # type: int
F_DUPFD = ...  # type: int
F_EXLCK = ...  # type: int
F_GETFD = ...  # type: int
F_GETFL = ...  # type: int
F_GETLEASE = ...  # type: int
F_GETLK = ...  # type: int
F_GETLK64 = ...  # type: int
F_GETOWN = ...  # type: int
F_GETSIG = ...  # type: int
F_NOTIFY = ...  # type: int
F_RDLCK = ...  # type: int
F_SETFD = ...  # type: int
F_SETFL = ...  # type: int
F_SETLEASE = ...  # type: int
F_SETLK = ...  # type: int
F_SETLK64 = ...  # type: int
F_SETLKW = ...  # type: int
F_SETLKW64 = ...  # type: int
F_SETOWN = ...  # type: int
F_SETSIG = ...  # type: int
F_SHLCK = ...  # type: int
F_UNLCK = ...  # type: int
F_WRLCK = ...  # type: int
I_ATMARK = ...  # type: int
I_CANPUT = ...  # type: int
I_CKBAND = ...  # type: int
I_FDINSERT = ...  # type: int
I_FIND = ...  # type: int
I_FLUSH = ...  # type: int
I_FLUSHBAND = ...  # type: int
I_GETBAND = ...  # type: int
I_GETCLTIME = ...  # type: int
I_GETSIG = ...  # type: int
I_GRDOPT = ...  # type: int
I_GWROPT = ...  # type: int
I_LINK = ...  # type: int
I_LIST = ...  # type: int
I_LOOK = ...  # type: int
I_NREAD = ...  # type: int
I_PEEK = ...  # type: int
I_PLINK = ...  # type: int
I_POP = ...  # type: int
I_PUNLINK = ...  # type: int
I_PUSH = ...  # type: int
I_RECVFD = ...  # type: int
I_SENDFD = ...  # type: int
I_SETCLTIME = ...  # type: int
I_SETSIG = ...  # type: int
I_SRDOPT = ...  # type: int
I_STR = ...  # type: int
I_SWROPT = ...  # type: int
I_UNLINK = ...  # type: int
LOCK_EX = ...  # type: int
LOCK_MAND = ...  # type: int
LOCK_NB = ...  # type: int
LOCK_READ = ...  # type: int
LOCK_RW = ...  # type: int
LOCK_SH = ...  # type: int
LOCK_UN = ...  # type: int
LOCK_WRITE = ...  # type: int

_ANYFILE = Union[int, io.IOBase]

# TODO All these return either int or bytes depending on the value of
# cmd (not on the type of arg).
def fcntl(fd: _ANYFILE, op: int, arg: Union[int, bytes] = ...) -> Any: ...

# TODO: arg: int or read-only buffer interface or read-write buffer interface
def ioctl(fd: _ANYFILE, op: int, arg: Union[int, bytes] = ...,
          mutate_flag: bool = ...) -> Any: ...

def flock(fd: _ANYFILE, op: int) -> None: ...
def lockf(fd: _ANYFILE, op: int, length: int = ..., start: int = ...,
          whence: int = ...) -> Any: ...
