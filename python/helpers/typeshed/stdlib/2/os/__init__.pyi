# Stubs for os
# Ron Murawski <ron@horizonchess.com>

from builtins import OSError as error
from io import TextIOWrapper as _TextIOWrapper
from posix import stat_result as stat_result  # TODO: use this, see https://github.com/python/mypy/issues/3078
import sys
from typing import (
    Mapping, MutableMapping, Dict, List, Any, Tuple, Iterator, overload, Union, AnyStr,
    Optional, Generic, Set, Callable, Text, Sequence, IO, NamedTuple, TypeVar
)
from . import path as path
from mypy_extensions import NoReturn

_T = TypeVar('_T')

# ----- os variables -----

if sys.version_info >= (3, 2):
    supports_bytes_environ: bool

if sys.version_info >= (3, 3):
    supports_dir_fd: Set[Callable[..., Any]]
    supports_fd: Set[Callable[..., Any]]
    supports_effective_ids: Set[Callable[..., Any]]
    supports_follow_symlinks: Set[Callable[..., Any]]

SEEK_SET: int
SEEK_CUR: int
SEEK_END: int

O_RDONLY: int
O_WRONLY: int
O_RDWR: int
O_APPEND: int
O_CREAT: int
O_EXCL: int
O_TRUNC: int
O_DSYNC: int    # Unix only
O_RSYNC: int    # Unix only
O_SYNC: int     # Unix only
O_NDELAY: int   # Unix only
O_NONBLOCK: int  # Unix only
O_NOCTTY: int   # Unix only
O_SHLOCK: int   # Unix only
O_EXLOCK: int   # Unix only
O_BINARY: int     # Windows only
O_NOINHERIT: int  # Windows only
O_SHORT_LIVED: int  # Windows only
O_TEMPORARY: int  # Windows only
O_RANDOM: int     # Windows only
O_SEQUENTIAL: int  # Windows only
O_TEXT: int       # Windows only
O_ASYNC: int      # Gnu extension if in C library
O_DIRECT: int     # Gnu extension if in C library
O_DIRECTORY: int  # Gnu extension if in C library
O_NOFOLLOW: int   # Gnu extension if in C library
O_NOATIME: int    # Gnu extension if in C library
O_LARGEFILE: int  # Gnu extension if in C library

curdir: str
pardir: str
sep: str
altsep: str
extsep: str
pathsep: str
defpath: str
linesep: str
devnull: str
name: str

F_OK: int
R_OK: int
W_OK: int
X_OK: int

class _Environ(MutableMapping[AnyStr, AnyStr], Generic[AnyStr]):
    def copy(self) -> Dict[AnyStr, AnyStr]: ...

environ: _Environ[str]
if sys.version_info >= (3, 2):
    environb: _Environ[bytes]

confstr_names: Dict[str, int]  # Unix only
pathconf_names: Dict[str, int]  # Unix only
sysconf_names: Dict[str, int]  # Unix only

EX_OK: int        # Unix only
EX_USAGE: int     # Unix only
EX_DATAERR: int   # Unix only
EX_NOINPUT: int   # Unix only
EX_NOUSER: int    # Unix only
EX_NOHOST: int    # Unix only
EX_UNAVAILABLE: int  # Unix only
EX_SOFTWARE: int  # Unix only
EX_OSERR: int     # Unix only
EX_OSFILE: int    # Unix only
EX_CANTCREAT: int  # Unix only
EX_IOERR: int     # Unix only
EX_TEMPFAIL: int  # Unix only
EX_PROTOCOL: int  # Unix only
EX_NOPERM: int    # Unix only
EX_CONFIG: int    # Unix only
EX_NOTFOUND: int  # Unix only

P_NOWAIT: int
P_NOWAITO: int
P_WAIT: int
if sys.platform == 'win32':
    P_DETACH: int  # Windows only
    P_OVERLAY: int  # Windows only

# wait()/waitpid() options
WNOHANG: int  # Unix only
WCONTINUED: int  # some Unix systems
WUNTRACED: int  # Unix only

TMP_MAX: int  # Undocumented, but used by tempfile

# ----- os classes (structures) -----
if sys.version_info >= (3, 6):
    from builtins import _PathLike as PathLike  # See comment in builtins

_PathType = path._PathType

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

@overload
def getenv(key: Text) -> Optional[str]: ...
@overload
def getenv(key: Text, default: _T) -> Union[str, _T]: ...
def putenv(key: Union[bytes, Text], value: Union[bytes, Text]) -> None: ...
def unsetenv(key: Union[bytes, Text]) -> None: ...

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
# TODO: add ns, dir_fd, follow_symlinks argument
if sys.version_info >= (3, 0):
    def utime(path: _PathType, times: Optional[Tuple[float, float]] = ...) -> None: ...
else:
    def utime(path: _PathType, times: Optional[Tuple[float, float]]) -> None: ...

if sys.version_info >= (3, 6):
    def walk(top: Union[AnyStr, PathLike[AnyStr]], topdown: bool = ...,
             onerror: Optional[Callable[[OSError], Any]] = ...,
             followlinks: bool = ...) -> Iterator[Tuple[AnyStr, List[AnyStr],
                                                        List[AnyStr]]]: ...
else:
    def walk(top: AnyStr, topdown: bool = ..., onerror: Optional[Callable[[OSError], Any]] = ...,
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
def plock(op: int) -> None: ...  # Unix only ???op is int?

if sys.version_info >= (3, 0):
    class popen(_TextIOWrapper):
        # TODO 'b' modes or bytes command not accepted?
        def __init__(self, command: str, mode: str = ...,
                     bufsize: int = ...) -> None: ...
        def close(self) -> Any: ...  # may return int
else:
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

if sys.version_info >= (3, 0):
    def sched_getaffinity(id: int) -> Set[int]: ...
if sys.version_info >= (3, 3):
    class waitresult:
        si_pid: int
    def waitid(idtype: int, id: int, options: int) -> waitresult: ...

if sys.version_info < (3, 0):
    def tmpfile() -> IO[Any]: ...
    def tmpnam() -> str: ...
    def tempnam(dir: str = ..., prefix: str = ...) -> str: ...

P_ALL: int
WEXITED: int
WNOWAIT: int

if sys.version_info >= (3, 3):
    def sync() -> None: ...  # Unix only

    def truncate(path: Union[_PathType, int], length: int) -> None: ...  # Unix only up to version 3.4

    def fwalk(top: AnyStr = ..., topdown: bool = ...,
              onerror: Callable = ..., *, follow_symlinks: bool = ...,
              dir_fd: int = ...) -> Iterator[Tuple[AnyStr, List[AnyStr],
                                             List[AnyStr], int]]: ...  # Unix only

    terminal_size = NamedTuple('terminal_size', [('columns', int), ('lines', int)])
    def get_terminal_size(fd: int = ...) -> terminal_size: ...

if sys.version_info >= (3, 4):
    def cpu_count() -> Optional[int]: ...
