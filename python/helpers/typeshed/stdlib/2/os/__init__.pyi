# created from https://docs.python.org/2/library/os.html

from typing import (
    Mapping, MutableMapping, Dict, List, Any, Tuple, Iterator, overload, Union, AnyStr,
    Optional, Generic, Set, Callable, Text, Sequence, IO, NamedTuple
)
from . import path
from mypy_extensions import NoReturn

error = OSError
SEEK_SET = 0
SEEK_CUR = 0
SEEK_END = 0

# More constants, copied from stdlib/3/os/__init__.pyi
O_RDONLY = 0
O_WRONLY = 0
O_RDWR = 0
O_APPEND = 0
O_CREAT = 0
O_EXCL = 0
O_TRUNC = 0
O_DSYNC = 0    # Unix only
O_RSYNC = 0    # Unix only
O_SYNC = 0     # Unix only
O_NDELAY = 0   # Unix only
O_NONBLOCK = 0  # Unix only
O_NOCTTY = 0   # Unix only
O_SHLOCK = 0   # Unix only
O_EXLOCK = 0   # Unix only
O_BINARY = 0     # Windows only
O_NOINHERIT = 0  # Windows only
O_SHORT_LIVED = 0  # Windows only
O_TEMPORARY = 0  # Windows only
O_RANDOM = 0     # Windows only
O_SEQUENTIAL = 0  # Windows only
O_TEXT = 0       # Windows only
O_ASYNC = 0      # Gnu extension if in C library
O_DIRECT = 0     # Gnu extension if in C library
O_DIRECTORY = 0  # Gnu extension if in C library
O_NOFOLLOW = 0   # Gnu extension if in C library
O_NOATIME = 0    # Gnu extension if in C library
O_LARGEFILE = 0  # Gnu extension if in C library

curdir = ...  # type: str
pardir = ...  # type: str
sep = ...  # type: str
altsep = ...  # type: str
extsep = ...  # type: str
pathsep = ...  # type: str
defpath = ...  # type: str
linesep = ...  # type: str
devnull = ...  # type: str
name = ...  # type: str

F_OK = 0
R_OK = 0
W_OK = 0
X_OK = 0

class _Environ(MutableMapping[str, str]):
    def copy(self) -> Dict[str, str]: ...

environ = ...  # type: _Environ
confstr_names = ...  # type: Mapping[str, int]  # Unix only
pathconf_names = ...  # type: Mapping[str, int]  # Unix only
sysconf_names = ...  # type: Mapping[str, int]  # Unix only
EX_OK = 0        # Unix only
EX_USAGE = 0     # Unix only
EX_DATAERR = 0   # Unix only
EX_NOINPUT = 0   # Unix only
EX_NOUSER = 0    # Unix only
EX_NOHOST = 0    # Unix only
EX_UNAVAILABLE = 0  # Unix only
EX_SOFTWARE = 0  # Unix only
EX_OSERR = 0     # Unix only
EX_OSFILE = 0    # Unix only
EX_CANTCREAT = 0  # Unix only
EX_IOERR = 0     # Unix only
EX_TEMPFAIL = 0  # Unix only
EX_PROTOCOL = 0  # Unix only
EX_NOPERM = 0    # Unix only
EX_CONFIG = 0    # Unix only
P_NOWAIT = 0
P_NOWAITO = 0
P_WAIT = 0
# P_DETACH = 0  # Windows only
# P_OVERLAY = 0  # Windows only

# wait()/waitpid() options
WNOHANG = 0  # Unix only
WCONTINUED = 0  # some Unix systems
WUNTRACED = 0  # Unix only

TMP_MAX = 0  # Undocumented, but used by tempfile
_PathType = Union[bytes, Text]
_StatVFS = NamedTuple('_StatVFS', [('f_bsize', int), ('f_frsize', int), ('f_blocks', int),
                                   ('f_bfree', int), ('f_bavail', int), ('f_files', int),
                                   ('f_ffree', int), ('f_favail', int), ('f_flag', int),
                                   ('f_namemax', int)])
def ctermid() -> str: ...  # Unix only
def getegid() -> int: ...  # Unix only
def geteuid() -> int: ...  # Unix only
def getgid() -> int: ...   # Unix only
def getgroups() -> List[int]: ...  # Unix only, behaves differently on Mac
def initgroups(username: str, gid: int) -> None: ...  # Unix only
def getlogin() -> str: ...
def getpgid(pid: int) -> int: ...  # Unix only
def getpgrp() -> int: ...  # Unix only
def getpid() -> int: ...
def getppid() -> int: ...
def getresuid() -> Tuple[int, int, int]: ...  # Unix only
def getresgid() -> Tuple[int, int, int]: ...  # Unix only
def getuid() -> int: ...  # Unix only
def getenv(varname: unicode, value: unicode = ...) -> str: ...
def putenv(varname: unicode, value: unicode) -> None: ...
def setegid(egid: int) -> None: ...  # Unix only
def seteuid(euid: int) -> None: ...  # Unix only
def setgid(gid: int) -> None: ...  # Unix only
def setgroups(groups: Sequence[int]) -> None: ...  # Unix only
def setpgrp() -> None: ...  # Unix only
def setpgid(pid: int, pgrp: int) -> None: ...  # Unix only
def setregid(rgid: int, egid: int) -> None: ...  # Unix only
def setresgid(rgid: int, egid: int, sgid: int) -> None: ...  # Unix only
def setresuid(ruid: int, euid: int, suid: int) -> None: ...  # Unix only
def setreuid(ruid: int, euid: int) -> None: ...  # Unix only
def getsid(pid: int) -> int: ...  # Unix only
def setsid() -> None: ...  # Unix only
def setuid(uid: int) -> None: ...  # Unix only
def strerror(code: int) -> str: ...
def umask(mask: int) -> int: ...
def uname() -> Tuple[str, str, str, str, str]: ...  # Unix only
def unsetenv(varname: str) -> None: ...
def fdopen(fd: int, *args, **kwargs) -> IO[Any]: ...
def close(fd: int) -> None: ...
def closerange(fd_low: int, fd_high: int) -> None: ...
def dup(fd: int) -> int: ...
def dup2(fd: int, fd2: int) -> None: ...
def fchmod(fd: int, mode: int) -> None: ...  # Unix only
def fchown(fd: int, uid: int, gid: int) -> None: ...  # Unix only
def fdatasync(fd: int) -> None: ...  # Unix only, not Mac
def fpathconf(fd: int, name: Union[str, int]) -> int: ...  # Unix only
def fstat(fd: int) -> Any: ...
def fstatvfs(fd: int) -> _StatVFS: ...  # Unix only
def fsync(fd: int) -> None: ...
def ftruncate(fd: int, length: int) -> None: ...  # Unix only
def isatty(fd: int) -> bool: ...  # Unix only
def lseek(fd: int, pos: int, how: int) -> int: ...
def open(file: _PathType, flags: int, mode: int = ...) -> int: ...
def openpty() -> Tuple[int, int]: ...  # some flavors of Unix
def pipe() -> Tuple[int, int]: ...
def read(fd: int, n: int) -> bytes: ...
def tcgetpgrp(fd: int) -> int: ...  # Unix only
def tcsetpgrp(fd: int, pg: int) -> None: ...  # Unix only
def ttyname(fd: int) -> str: ...  # Unix only
def write(fd: int, string: bytes) -> int: ...
def access(path: _PathType, mode: int) -> bool: ...
def chdir(path: _PathType) -> None: ...
def fchdir(fd: int) -> None: ...
def getcwd() -> str: ...
def getcwdu() -> unicode: ...
def chflags(path: _PathType, flags: int) -> None: ...  # Unix only
def chroot(path: _PathType) -> None: ...  # Unix only
def chmod(path: _PathType, mode: int) -> None: ...
def chown(path: _PathType, uid: int, gid: int) -> None: ...  # Unix only
def lchflags(path: _PathType, flags: int) -> None: ...  # Unix only
def lchmod(path: _PathType, mode: int) -> None: ...  # Unix only
def lchown(path: _PathType, uid: int, gid: int) -> None: ...  # Unix only
def link(src: _PathType, link_name: _PathType) -> None: ...
def listdir(path: AnyStr) -> List[AnyStr]: ...
def lstat(path: _PathType) -> Any: ...
def mkfifo(path: _PathType, mode: int = ...) -> None: ...  # Unix only
def mknod(filename: _PathType, mode: int = ..., device: int = ...) -> None: ...
def major(device: int) -> int: ...
def minor(device: int) -> int: ...
def makedev(major: int, minor: int) -> int: ...
def mkdir(path: _PathType, mode: int = ...) -> None: ...
def makedirs(path: _PathType, mode: int = ...) -> None: ...
def pathconf(path: _PathType, name: Union[str, int]) -> int: ...  # Unix only
def readlink(path: AnyStr) -> AnyStr: ...
def remove(path: _PathType) -> None: ...
def removedirs(path: _PathType) -> None: ...
def rename(src: _PathType, dst: _PathType) -> None: ...
def renames(old: _PathType, new: _PathType) -> None: ...
def rmdir(path: _PathType) -> None: ...
def stat(path: _PathType) -> Any: ...
@overload
def stat_float_times(newvalue: bool = ...) -> None: ...
@overload
def stat_float_times() -> bool: ...
def statvfs(path: _PathType) -> _StatVFS: ...  # Unix only
def symlink(source: _PathType, link_name: _PathType) -> None: ...
def unlink(path: _PathType) -> None: ...
def utime(path: _PathType, times: Optional[Tuple[float, float]]) -> None: ...

# TODO onerror: function from OSError to void
def walk(top: AnyStr, topdown: bool = ..., onerror: Any = ...,
         followlinks: bool = ...) -> Iterator[Tuple[AnyStr, List[AnyStr],
                                                    List[AnyStr]]]: ...

def abort() -> NoReturn: ...
# These are defined as execl(file, *args) but the first *arg is mandatory.
def execl(file: _PathType, __arg0: Union[bytes, Text], *args: Union[bytes, Text]) -> NoReturn: ...
def execlp(file: _PathType, __arg0: Union[bytes, Text], *args: Union[bytes, Text]) -> NoReturn: ...

# These are: execle(file, *args, env) but env is pulled from the last element of the args.
def execle(file: _PathType, __arg0: Union[bytes, Text], *args: Any) -> NoReturn: ...
def execlpe(file: _PathType, __arg0: Union[bytes, Text], *args: Any) -> NoReturn: ...

# The docs say `args: tuple or list of strings`
# The implementation enforces tuple or list so we can't use Sequence.
_ExecVArgs = Union[Tuple[Union[bytes, Text], ...], List[bytes], List[Text], List[Union[bytes, Text]]]
def execv(path: _PathType, args: _ExecVArgs) -> None: ...
def execve(path: _PathType, args: _ExecVArgs, env: Mapping[str, str]) -> None: ...
def execvp(file: _PathType, args: _ExecVArgs) -> None: ...
def execvpe(file: _PathType, args: _ExecVArgs, env: Mapping[str, str]) -> None: ...

def _exit(n: int) -> NoReturn: ...
def fork() -> int: ...  # Unix only
def forkpty() -> Tuple[int, int]: ...  # some flavors of Unix
def kill(pid: int, sig: int) -> None: ...
def killpg(pgid: int, sig: int) -> None: ...  # Unix only
def nice(increment: int) -> int: ...  # Unix only
# TODO: plock, popen*, P_*
def popen(command: str, *args, **kwargs) -> Optional[IO[Any]]: ...
def popen2(cmd: str, *args, **kwargs) -> Tuple[IO[Any], IO[Any]]: ...
def popen3(cmd: str, *args, **kwargs) -> Tuple[IO[Any], IO[Any], IO[Any]]: ...
def popen4(cmd: str, *args, **kwargs) -> Tuple[IO[Any], IO[Any]]: ...

def spawnl(mode: int, path: _PathType, arg0: Union[bytes, Text], *args: Union[bytes, Text]) -> int: ...
def spawnle(mode: int, path: _PathType, arg0: Union[bytes, Text],
            *args: Any) -> int: ...  # Imprecise sig
def spawnlp(mode: int, file: _PathType, arg0: Union[bytes, Text],
            *args: Union[bytes, Text]) -> int: ...  # Unix only TODO
def spawnlpe(mode: int, file: _PathType, arg0: Union[bytes, Text], *args: Any) -> int:
    ...  # Imprecise signature; Unix only TODO
def spawnv(mode: int, path: _PathType, args: List[Union[bytes, Text]]) -> int: ...
def spawnve(mode: int, path: _PathType, args: List[Union[bytes, Text]],
            env: Mapping[str, str]) -> int: ...
def spawnvp(mode: int, file: _PathType, args: List[Union[bytes, Text]]) -> int: ...  # Unix only
def spawnvpe(mode: int, file: _PathType, args: List[Union[bytes, Text]],
             env: Mapping[str, str]) -> int:
    ...  # Unix only
def startfile(path: _PathType, operation: Optional[str] = ...) -> None: ...  # Windows only
def system(command: _PathType) -> int: ...
def times() -> Tuple[float, float, float, float, float]: ...
def wait() -> Tuple[int, int]: ...  # Unix only
def waitpid(pid: int, options: int) -> Tuple[int, int]: ...
def wait3(options: int) -> Tuple[int, int, Any]: ...  # Unix only
def wait4(pid: int, options: int) -> Tuple[int, int, Any]: ...  # Unix only
def WCOREDUMP(status: int) -> bool: ...  # Unix only
def WIFCONTINUED(status: int) -> bool: ...  # Unix only
def WIFSTOPPED(status: int) -> bool: ...  # Unix only
def WIFSIGNALED(status: int) -> bool: ...  # Unix only
def WIFEXITED(status: int) -> bool: ...  # Unix only
def WEXITSTATUS(status: int) -> int: ...  # Unix only
def WSTOPSIG(status: int) -> int: ...  # Unix only
def WTERMSIG(status: int) -> int: ...  # Unix only
def confstr(name: Union[str, int]) -> Optional[str]: ...  # Unix only
def getloadavg() -> Tuple[float, float, float]: ...  # Unix only
def sysconf(name: Union[str, int]) -> int: ...  # Unix only
def urandom(n: int) -> bytes: ...

def tmpfile() -> IO[Any]: ...
def tmpnam() -> str: ...
def tempnam(dir: str = ..., prefix: str = ...) -> str: ...

P_ALL = 0
WEXITED = 0
WNOWAIT = 0
