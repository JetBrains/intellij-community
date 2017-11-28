# Stubs for os
# Ron Murawski <ron@horizonchess.com>

from builtins import OSError as error
from io import TextIOWrapper as _TextIOWrapper
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

if sys.version_info >= (3, 6):
    class DirEntry(PathLike[AnyStr]):
        # This is what the scandir interator yields
        # The constructor is hidden

        name: AnyStr
        path: AnyStr
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

        name: AnyStr
        path: AnyStr
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

    st_mode: int  # protection bits,
    st_ino: int  # inode number,
    st_dev: int  # device,
    st_nlink: int  # number of hard links,
    st_uid: int  # user id of owner,
    st_gid: int  # group id of owner,
    st_size: int  # size of file, in bytes,
    st_atime: float  # time of most recent access,
    st_mtime: float  # time of most recent content modification,
    st_ctime: float  # platform dependent (time of most recent metadata change on Unix, or the time of creation on Windows)

    if sys.version_info >= (3, 3):
        st_atime_ns: int  # time of most recent access, in nanoseconds
        st_mtime_ns: int  # time of most recent content modification in nanoseconds
        st_ctime_ns: int  # platform dependent (time of most recent metadata change on Unix, or the time of creation on Windows) in nanoseconds

    # not documented
    def __init__(self, tuple: Tuple[int, ...]) -> None: ...

    # On some Unix systems (such as Linux), the following attributes may also
    # be available:
    st_blocks: int  # number of blocks allocated for file
    st_blksize: int  # filesystem blocksize
    st_rdev: int  # type of device if an inode device
    st_flags: int  # user defined flags for file

    # On other Unix systems (such as FreeBSD), the following attributes may be
    # available (but may be only filled out if root tries to use them):
    st_gen: int  # file generation number
    st_birthtime: int  # time of file creation

    # On Mac OS systems, the following attributes may also be available:
    st_rsize: int
    st_creator: int
    st_type: int

class statvfs_result:  # Unix only
    f_bsize: int
    f_frsize: int
    f_blocks: int
    f_bfree: int
    f_bavail: int
    f_files: int
    f_ffree: int
    f_favail: int
    f_flag: int
    f_namemax: int

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
if sys.version_info >= (3, 3):
    from posix import uname_result
    def uname() -> uname_result: ...  # Unix only
else:
    def uname() -> Tuple[str, str, str, str, str]: ...  # Unix only

@overload
def getenv(key: Text) -> Optional[str]: ...
@overload
def getenv(key: Text, default: _T) -> Union[str, _T]: ...
def getenvb(key: bytes, default: bytes = ...) -> bytes: ...
def putenv(key: Union[bytes, Text], value: Union[bytes, Text]) -> None: ...
def unsetenv(key: Union[bytes, Text]) -> None: ...

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
if sys.version_info >= (3, 4):
    def makedirs(name: _PathType, mode: int = ...,
                 exist_ok: bool = ...) -> None: ...
else:
    def makedirs(path: _PathType, mode: int = ...,
                 exist_ok: bool = ...) -> None: ...
def pathconf(path: _PathType, name: Union[str, int]) -> int: ...  # Unix only
if sys.version_info >= (3, 6):
    def readlink(path: Union[AnyStr, PathLike[AnyStr]]) -> AnyStr: ...
else:
    def readlink(path: AnyStr) -> AnyStr: ...
def remove(path: _PathType) -> None: ...
if sys.version_info >= (3, 4):
    def removedirs(name: _PathType) -> None: ...
else:
    def removedirs(path: _PathType) -> None: ...
def rename(src: _PathType, dst: _PathType) -> None: ...
def renames(old: _PathType, new: _PathType) -> None: ...
if sys.version_info >= (3, 3):
    def replace(src: _PathType, dst: _PathType) -> None: ...
def rmdir(path: _PathType) -> None: ...
if sys.version_info >= (3, 6):
    @overload
    def scandir() -> Iterator[DirEntry[str]]: ...
    @overload
    def scandir(path: Union[AnyStr, PathLike[AnyStr]]) -> Iterator[DirEntry[AnyStr]]: ...
elif sys.version_info >= (3, 5):
    @overload
    def scandir() -> Iterator[DirEntry[str]]: ...
    @overload
    def scandir(path: AnyStr) -> Iterator[DirEntry[AnyStr]]: ...
def stat(path: _PathType) -> stat_result: ...
def stat_float_times(newvalue: Union[bool, None] = ...) -> bool: ...
def statvfs(path: _PathType) -> statvfs_result: ...  # Unix only
def symlink(source: _PathType, link_name: _PathType,
            target_is_directory: bool = ...) -> None:
    ...  # final argument in Windows only
def unlink(path: _PathType) -> None: ...
if sys.version_info >= (3, 0):
    def utime(path: _PathType, times: Optional[Union[Tuple[int, int], Tuple[float, float]]] = ...,
              ns: Optional[Tuple[int, int]] = ..., dir_fd: Optional[int] = ...,
              follow_symlinks: bool = ...) -> None: ...
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
