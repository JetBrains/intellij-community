# Stubs for os
# Ron Murawski <ron@horizonchess.com>

# based on http: //docs.python.org/3.2/library/os.html

from builtins import OSError as error
from io import TextIOWrapper as _TextIOWrapper
import sys
from typing import (
    Mapping, MutableMapping, Dict, List, Any, Tuple, Iterator, overload, Union, AnyStr,
    Optional, Generic, Set, Callable, Text, Sequence
)
from . import path
from mypy_extensions import NoReturn

# ----- os variables -----

supports_bytes_environ = False  # TODO: True when bytes implemented?

if sys.version_info >= (3, 3):
    supports_dir_fd = ...  # type: Set[Callable[..., Any]]
    supports_fd = ...  # type: Set[Callable[..., Any]]
    supports_effective_ids = ...  # type: Set[Callable[..., Any]]
    supports_follow_symlinks = ...  # type: Set[Callable[..., Any]]

SEEK_SET = 0
SEEK_CUR = 0
SEEK_END = 0

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

class _Environ(MutableMapping[AnyStr, AnyStr], Generic[AnyStr]):
    def copy(self) -> Dict[AnyStr, AnyStr]: ...

environ = ...  # type: _Environ[str]
environb = ...  # type: _Environ[bytes]

confstr_names = ...  # type: Dict[str, int]  # Unix only
pathconf_names = ...  # type: Dict[str, int]  # Unix only
sysconf_names = ...  # type: Dict[str, int]  # Unix only

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
EX_NOTFOUND = 0  # Unix only

P_NOWAIT = 0
P_NOWAITO = 0
P_WAIT = 0
# P_DETACH = 0  # Windows only
# P_OVERLAY = 0  # Windows only

# wait()/waitpid() options
WNOHANG = 0  # Unix only
# WCONTINUED = 0  # some Unix systems
# WUNTRACED = 0  # Unix only

TMP_MAX = 0  # Undocumented, but used by tempfile

# ----- os classes (structures) -----
if sys.version_info >= (3, 6):
    from builtins import _PathLike as PathLike  # See comment in builtins

_PathType = Union[bytes, Text]

if sys.version_info >= (3, 6):
    class DirEntry(PathLike[AnyStr]):
        # This is what the scandir interator yields
        # The constructor is hidden

        name = ...  # type: AnyStr
        path = ...  # type: AnyStr
        def inode(self) -> int: ...
        def is_dir(self, follow_symlinks: bool = ...) -> bool: ...
        def is_file(self, follow_symlinks: bool = ...) -> bool: ...
        def is_symlink(self) -> bool: ...
        def stat(self) -> stat_result: ...

        def __fspath__(self) -> AnyStr: ...
elif sys.version_info >= (3, 5):
    class DirEntry(Generic[AnyStr]):
        # This is what the scandir interator yields
        # The constructor is hidden

        name = ...  # type: AnyStr
        path = ...  # type: AnyStr
        def inode(self) -> int: ...
        def is_dir(self, follow_symlinks: bool = ...) -> bool: ...
        def is_file(self, follow_symlinks: bool = ...) -> bool: ...
        def is_symlink(self) -> bool: ...
        def stat(self) -> stat_result: ...


class stat_result:
    # For backward compatibility, the return value of stat() is also
    # accessible as a tuple of at least 10 integers giving the most important
    # (and portable) members of the stat structure, in the order st_mode,
    # st_ino, st_dev, st_nlink, st_uid, st_gid, st_size, st_atime, st_mtime,
    # st_ctime. More items may be added at the end by some implementations.

    st_mode = 0  # protection bits,
    st_ino = 0  # inode number,
    st_dev = 0  # device,
    st_nlink = 0  # number of hard links,
    st_uid = 0  # user id of owner,
    st_gid = 0  # group id of owner,
    st_size = 0  # size of file, in bytes,
    st_atime = 0.0  # time of most recent access,
    st_mtime = 0.0  # time of most recent content modification,
    st_ctime = 0.0  # platform dependent (time of most recent metadata change on Unix, or the time of creation on Windows)

    if sys.version_info >= (3, 3):
        st_atime_ns = 0  # time of most recent access, in nanoseconds
        st_mtime_ns = 0  # time of most recent content modification in nanoseconds
        st_ctime_ns = 0  # platform dependent (time of most recent metadata change on Unix, or the time of creation on Windows) in nanoseconds

    # not documented
    def __init__(self, tuple: Tuple[int, ...]) -> None: ...

    # On some Unix systems (such as Linux), the following attributes may also
    # be available:
    st_blocks = 0  # number of blocks allocated for file
    st_blksize = 0  # filesystem blocksize
    st_rdev = 0  # type of device if an inode device
    st_flags = 0  # user defined flags for file

    # On other Unix systems (such as FreeBSD), the following attributes may be
    # available (but may be only filled out if root tries to use them):
    st_gen = 0  # file generation number
    st_birthtime = 0  # time of file creation

    # On Mac OS systems, the following attributes may also be available:
    st_rsize = 0
    st_creator = 0
    st_type = 0

class statvfs_result:  # Unix only
    f_bsize = 0
    f_frsize = 0
    f_blocks = 0
    f_bfree = 0
    f_bavail = 0
    f_files = 0
    f_ffree = 0
    f_favail = 0
    f_flag = 0
    f_namemax = 0

# ----- os function stubs -----
if sys.version_info >= (3, 6):
    def fsencode(filename: Union[str, bytes, PathLike]) -> bytes: ...
else:
    def fsencode(filename: Union[str, bytes]) -> bytes: ...

if sys.version_info >= (3, 6):
    def fsdecode(filename: Union[str, bytes, PathLike]) -> str: ...
else:
    def fsdecode(filename: Union[str, bytes]) -> str: ...

if sys.version_info >= (3, 6):
    @overload
    def fspath(path: str) -> str: ...
    @overload
    def fspath(path: bytes) -> bytes: ...
    @overload
    def fspath(path: PathLike) -> Any: ...

def get_exec_path(env: Optional[Mapping[str, str]] = ...) -> List[str]: ...
# NOTE: get_exec_path(): returns List[bytes] when env not None
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
def getenv(key: str, default: str = ...) -> str: ...
def getenvb(key: bytes, default: bytes = ...) -> bytes: ...
# TODO mixed str/bytes putenv arguments
def putenv(key: AnyStr, value: AnyStr) -> None: ...
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
def unsetenv(key: _PathType) -> None: ...
# Return IO or TextIO
def fdopen(fd: int, mode: str = ..., buffering: int = ..., encoding: str = ...,
           errors: str = ..., newline: str = ..., closefd: bool = ...) -> Any: ...
def close(fd: int) -> None: ...
def closerange(fd_low: int, fd_high: int) -> None: ...
def device_encoding(fd: int) -> Optional[str]: ...
def dup(fd: int) -> int: ...
def dup2(fd: int, fd2: int) -> None: ...
def fchmod(fd: int, mode: int) -> None: ...  # Unix only
def fchown(fd: int, uid: int, gid: int) -> None: ...  # Unix only
def fdatasync(fd: int) -> None: ...  # Unix only, not Mac
def fpathconf(fd: int, name: Union[str, int]) -> int: ...  # Unix only
def fstat(fd: int) -> stat_result: ...
def fstatvfs(fd: int) -> statvfs_result: ...  # Unix only
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
def getcwdb() -> bytes: ...
def chflags(path: _PathType, flags: int) -> None: ...  # Unix only
def chroot(path: _PathType) -> None: ...  # Unix only
def chmod(path: _PathType, mode: int) -> None: ...
def chown(path: _PathType, uid: int, gid: int) -> None: ...  # Unix only
def lchflags(path: _PathType, flags: int) -> None: ...  # Unix only
def lchmod(path: _PathType, mode: int) -> None: ...  # Unix only
def lchown(path: _PathType, uid: int, gid: int) -> None: ...  # Unix only
def link(src: _PathType, link_name: _PathType) -> None: ...

@overload
def listdir(path: str = ...) -> List[str]: ...
@overload
def listdir(path: bytes) -> List[bytes]: ...

def lstat(path: _PathType) -> stat_result: ...
def mkfifo(path: _PathType, mode: int = ...) -> None: ...  # Unix only
def mknod(filename: _PathType, mode: int = ..., device: int = ...) -> None: ...
def major(device: int) -> int: ...
def minor(device: int) -> int: ...
def makedev(major: int, minor: int) -> int: ...
def mkdir(path: _PathType, mode: int = ...) -> None: ...
def makedirs(path: _PathType, mode: int = ...,
             exist_ok: bool = ...) -> None: ...
def pathconf(path: _PathType, name: Union[str, int]) -> int: ...  # Unix only
def readlink(path: AnyStr) -> AnyStr: ...
def remove(path: _PathType) -> None: ...
def removedirs(path: _PathType) -> None: ...
def rename(src: _PathType, dst: _PathType) -> None: ...
def renames(old: _PathType, new: _PathType) -> None: ...
if sys.version_info >= (3, 3):
    def replace(src: _PathType, dst: _PathType) -> None: ...
def rmdir(path: _PathType) -> None: ...
if sys.version_info >= (3, 5):
    @overload
    def scandir(path: str = ...) -> Iterator[DirEntry[str]]: ...
    @overload
    def scandir(path: bytes) -> Iterator[DirEntry[bytes]]: ...
def stat(path: _PathType) -> stat_result: ...
def stat_float_times(newvalue: Union[bool, None] = ...) -> bool: ...
def statvfs(path: _PathType) -> statvfs_result: ...  # Unix only
def symlink(source: _PathType, link_name: _PathType,
            target_is_directory: bool = ...) -> None:
    ...  # final argument in Windows only
def unlink(path: _PathType) -> None: ...
def utime(path: _PathType, times: Optional[Tuple[float, float]] = ...) -> None: ...

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
def plock(op: int) -> None: ...  # Unix only ???op is int?

class popen(_TextIOWrapper):
    # TODO 'b' modes or bytes command not accepted?
    def __init__(self, command: str, mode: str = ...,
                 bufsize: int = ...) -> None: ...
    def close(self) -> Any: ...  # may return int

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

def sched_getaffinity(id: int) -> Set[int]: ...
class waitresult:
    si_pid = 0
def waitid(idtype: int, id: int, options: int) -> waitresult: ...
P_ALL = 0
WEXITED = 0
WNOWAIT = 0

if sys.version_info >= (3, 3):
    def sync() -> None: ...  # Unix only

    def truncate(path: Union[_PathType, int], length: int) -> None: ...  # Unix only up to version 3.4

    def fwalk(top: AnyStr = ..., topdown: bool = ...,
              onerror: Callable = ..., *, follow_symlinks: bool = ...,
              dir_fd: int = ...) -> Iterator[Tuple[AnyStr, List[AnyStr],
                                             List[AnyStr], int]]: ...  # Unix only

    def get_terminal_size(fd: int = ...) -> Tuple[int, int]: ...

if sys.version_info >= (3, 4):
    def cpu_count() -> Optional[int]: ...
