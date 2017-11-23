# Stubs for os
# Ron Murawski <ron@horizonchess.com>

from builtins import OSError as error
from io import TextIOWrapper as _TextIOWrapper
import sys
from typing import (
    Mapping, MutableMapping, Dict, List, Any, Tuple, IO, Iterable, Iterator, overload, Union, AnyStr,
    Optional, Generic, Set, Callable, Text, Sequence, NamedTuple, TypeVar
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

    PRIO_PROCESS: int  # Unix only
    PRIO_PGRP: int  # Unix only
    PRIO_USER: int  # Unix only

    F_LOCK: int  # Unix only
    F_TLOCK: int  # Unix only
    F_ULOCK: int  # Unix only
    F_TEST: int  # Unix only

    POSIX_FADV_NORMAL: int  # Unix only
    POSIX_FADV_SEQUENTIAL: int  # Unix only
    POSIX_FADV_RANDOM: int  # Unix only
    POSIX_FADV_NOREUSE: int  # Unix only
    POSIX_FADV_WILLNEED: int  # Unix only
    POSIX_FADV_DONTNEED: int  # Unix only

    SF_NODISKIO: int  # Unix only
    SF_MNOWAIT: int  # Unix only
    SF_SYNC: int  # Unix only

    XATTR_SIZE_MAX: int  # Linux only
    XATTR_CREATE: int  # Linux only
    XATTR_REPLACE: int  # Linux only

    P_PID: int  # Unix only
    P_PGID: int  # Unix only
    P_ALL: int  # Unix only

    WEXITED: int  # Unix only
    WSTOPPED: int  # Unix only
    WNOWAIT: int  # Unix only

    CLD_EXITED: int  # Unix only
    CLD_DUMPED: int  # Unix only
    CLD_TRAPPED: int  # Unix only
    CLD_CONTINUED: int  # Unix only

    SCHED_OTHER: int  # some flavors of Unix
    SCHED_BATCH: int  # some flavors of Unix
    SCHED_IDLE: int  # some flavors of Unix
    SCHED_SPORADIC: int  # some flavors of Unix
    SCHED_FIFO: int  # some flavors of Unix
    SCHED_RR: int  # some flavors of Unix
    SCHED_RESET_ON_FORK: int  # some flavors of Unix

    RTLD_LAZY: int
    RTLD_NOW: int
    RTLD_GLOBAL: int
    RTLD_LOCAL: int
    RTLD_NODELETE: int
    RTLD_NOLOAD: int
    RTLD_DEEPBIND: int


SEEK_SET: int
SEEK_CUR: int
SEEK_END: int
if sys.version_info >= (3, 3):
    SEEK_DATA: int  # some flavors of Unix
    SEEK_HOLE: int  # some flavors of Unix

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
if sys.version_info >= (3, 3):
    O_CLOEXEC: int  # Unix only
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
if sys.version_info >= (3, 4):
    O_PATH: int  # Gnu extension if in C library
    O_TMPFILE: int  # Gnu extension if in C library
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
if sys.version_info >= (3, 3):
    _FdOrPathType = Union[int, _PathType]
else:
    _FdOrPathType = _PathType

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
if sys.version_info >= (3, 3):
    def getgrouplist(user: str, gid: int) -> List[int]: ...  # Unix only
def getgroups() -> List[int]: ...  # Unix only, behaves differently on Mac
def initgroups(username: str, gid: int) -> None: ...  # Unix only
def getlogin() -> str: ...
def getpgid(pid: int) -> int: ...  # Unix only
def getpgrp() -> int: ...  # Unix only
def getpid() -> int: ...
def getppid() -> int: ...
if sys.version_info >= (3, 3):
    def getpriority(which: int, who: int) -> int: ...  # Unix only
    def setpriority(which: int, who: int, priority: int) -> None: ...  # Unix only
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
if sys.version_info >= (3, 5):
    def get_blocking(fd: int) -> bool: ...  # Unix only
    def set_blocking(fd: int, blocking: bool) -> None: ...  # Unix only
def isatty(fd: int) -> bool: ...  # Unix only
if sys.version_info >= (3, 3):
    def lockf(__fd: int, __cmd: int, __length: int) -> None: ...  # Unix only
def lseek(fd: int, pos: int, how: int) -> int: ...
if sys.version_info >= (3, 3):
    def open(file: _PathType, flags: int, mode: int = ..., *, dir_fd: Optional[int] = ...) -> int: ...
else:
    def open(file: _PathType, flags: int, mode: int = ...) -> int: ...
def openpty() -> Tuple[int, int]: ...  # some flavors of Unix
def pipe() -> Tuple[int, int]: ...
if sys.version_info >= (3, 3):
    def pipe2(flags: int) -> Tuple[int, int]: ...  # some flavors of Unix
    def posix_fallocate(fd: int, offset: int, length: int) -> None: ...  # Unix only
    def posix_fadvise(fd: int, offset: int, length: int, advice: int) -> None: ...  # Unix only
    def pread(fd: int, buffersize: int, offset: int) -> bytes: ...  # Unix only
    def pwrite(fd: int, string: bytes, offset: int) -> int: ...  # Unix only
def read(fd: int, n: int) -> bytes: ...
if sys.version_info >= (3, 3):
    @overload
    def sendfile(__out_fd: int, __in_fd: int, offset: Optional[int], count: int) -> int: ...  # Unix only
    @overload
    def sendfile(__out_fd: int, __in_fd: int, offset: int, count: int,
                 headers: Sequence[bytes] = ..., trailers: Sequence[bytes] = ..., flags: int = ...) -> int: ...  # FreeBSD and Mac OS X only
    def readv(fd: int, buffers: Sequence[bytearray]) -> int: ...  # Unix only
    def writev(fd: int, buffers: Sequence[bytes]) -> int: ...  # Unix only

    terminal_size = NamedTuple('terminal_size', [('columns', int), ('lines', int)])
    def get_terminal_size(fd: int = ...) -> terminal_size: ...

if sys.version_info >= (3, 4):
    def get_inheritable(fd: int) -> bool: ...
    def set_inheritable(fd: int, inheritable: bool) -> None: ...

def tcgetpgrp(fd: int) -> int: ...  # Unix only
def tcsetpgrp(fd: int, pg: int) -> None: ...  # Unix only
def ttyname(fd: int) -> str: ...  # Unix only
def write(fd: int, string: bytes) -> int: ...
if sys.version_info >= (3, 3):
    def access(path: _FdOrPathType, mode: int, *, dir_fd: Optional[int] = ...,
               effective_ids: bool = ..., follow_symlinks: bool = ...) -> bool: ...
else:
    def access(path: _PathType, mode: int) -> bool: ...
def chdir(path: _FdOrPathType) -> None: ...
def fchdir(fd: int) -> None: ...
def getcwd() -> str: ...
def getcwdb() -> bytes: ...
if sys.version_info >= (3, 3):
    def chflags(path: _PathType, flags: int, follow_symlinks: bool = ...) -> None: ...  # some flavors of Unix
    def chmod(path: _FdOrPathType, mode: int, *, dir_fd: Optional[int] = ..., follow_symlinks: bool = ...) -> None: ...
    def chown(path: _FdOrPathType, uid: int, gid: int, *, dir_fd: Optional[int] = ..., follow_symlinks: bool = ...) -> None: ...  # Unix only
else:
    def chflags(path: _PathType, flags: int) -> None: ...  # Some flavors of Unix
    def chmod(path: _PathType, mode: int) -> None: ...
    def chown(path: _PathType, uid: int, gid: int) -> None: ...  # Unix only
def chroot(path: _PathType) -> None: ...  # Unix only
def lchflags(path: _PathType, flags: int) -> None: ...  # Unix only
def lchmod(path: _PathType, mode: int) -> None: ...  # Unix only
def lchown(path: _PathType, uid: int, gid: int) -> None: ...  # Unix only
if sys.version_info >= (3, 3):
    def link(src: _PathType, link_name: _PathType, *, src_dir_fd: Optional[int] = ...,
             dst_dir_fd: Optional[int] = ..., follow_symlinks: bool = ...) -> None: ...
else:
    def link(src: _PathType, link_name: _PathType) -> None: ...

if sys.version_info >= (3, 3):
    @overload
    def listdir(path: Optional[str] = ...) -> List[str]: ...
    @overload
    def listdir(path: bytes) -> List[bytes]: ...
    @overload
    def listdir(path: int) -> List[str]: ...
else:
    @overload
    def listdir(path: Optional[str] = ...) -> List[str]: ...
    @overload
    def listdir(path: bytes) -> List[bytes]: ...

if sys.version_info >= (3, 3):
    def lstat(path: _PathType, *, dir_fd: Optional[int] = ...) -> stat_result: ...
    def mkdir(path: _PathType, mode: int = ..., *, dir_fd: Optional[int] = ...) -> None: ...
    def mkfifo(path: _PathType, mode: int = ..., *, dir_fd: Optional[int] = ...) -> None: ...  # Unix only
else:
    def lstat(path: _PathType) -> stat_result: ...
    def mkdir(path: _PathType, mode: int = ...) -> None: ...
    def mkfifo(path: _PathType, mode: int = ...) -> None: ...  # Unix only
if sys.version_info >= (3, 4):
    def makedirs(name: _PathType, mode: int = ..., exist_ok: bool = ...) -> None: ...
else:
    def makedirs(path: _PathType, mode: int = ..., exist_ok: bool = ...) -> None: ...
if sys.version_info >= (3, 4):
    def mknod(path: _PathType, mode: int = ..., device: int = ...,
              *, dir_fd: Optional[int] = ...) -> None: ...
elif sys.version_info >= (3, 3):
    def mknod(filename: _PathType, mode: int = ..., device: int = ...,
              *, dir_fd: Optional[int] = ...) -> None: ...
else:
    def mknod(filename: _PathType, mode: int = ..., device: int = ...) -> None: ...
def major(device: int) -> int: ...
def minor(device: int) -> int: ...
def makedev(major: int, minor: int) -> int: ...
def pathconf(path: _FdOrPathType, name: Union[str, int]) -> int: ...  # Unix only
if sys.version_info >= (3, 6):
    def readlink(path: Union[AnyStr, PathLike[AnyStr]], *, dir_fd: Optional[int] = ...) -> AnyStr: ...
elif sys.version_info >= (3, 3):
    def readlink(path: AnyStr, *, dir_fd: Optional[int] = ...) -> AnyStr: ...
else:
    def readlink(path: AnyStr) -> AnyStr: ...
if sys.version_info >= (3, 3):
    def remove(path: _PathType, *, dir_fd: Optional[int] = ...) -> None: ...
else:
    def remove(path: _PathType) -> None: ...
if sys.version_info >= (3, 4):
    def removedirs(name: _PathType) -> None: ...
else:
    def removedirs(path: _PathType) -> None: ...
if sys.version_info >= (3, 3):
    def rename(src: _PathType, dst: _PathType, *,
               src_dir_fd: Optional[int] = ..., dst_dir_fd: Optional[int] = ...) -> None: ...
else:
    def rename(src: _PathType, dst: _PathType) -> None: ...
def renames(old: _PathType, new: _PathType) -> None: ...
if sys.version_info >= (3, 3):
    def replace(src: _PathType, dst: _PathType, *,
               src_dir_fd: Optional[int] = ..., dst_dir_fd: Optional[int] = ...) -> None: ...
    def rmdir(path: _PathType, *, dir_fd: Optional[int] = ...) -> None: ...
else:
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
if sys.version_info >= (3, 3):
    def stat(path: _FdOrPathType, *, dir_fd: Optional[int] = ...,
             follow_symlinks: bool = ...) -> stat_result: ...
else:
    def stat(path: _PathType) -> stat_result: ...
@overload
def stat_float_times() -> bool: ...
@overload
def stat_float_times(__newvalue: bool) -> None: ...
def statvfs(path: _FdOrPathType) -> statvfs_result: ...  # Unix only
if sys.version_info >= (3, 3):
    def symlink(source: _PathType, link_name: _PathType,
                target_is_directory: bool = ..., *, dir_fd: Optional[int] = ...) -> None: ...
    def sync() -> None: ...  # Unix only
    def truncate(path: _FdOrPathType, length: int) -> None: ...  # Unix only up to version 3.4
    def unlink(path: _PathType, *, dir_fd: Optional[int] = ...) -> None: ...
    def utime(path: _FdOrPathType, times: Optional[Union[Tuple[int, int], Tuple[float, float]]] = ..., *,
              ns: Tuple[int, int] = ..., dir_fd: Optional[int] = ...,
              follow_symlinks: bool = ...) -> None: ...
else:
    def symlink(source: _PathType, link_name: _PathType,
                target_is_directory: bool = ...) -> None:
        ...  # final argument in Windows only
    def unlink(path: _PathType) -> None: ...
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
if sys.version_info >= (3, 3):
    def fwalk(top: _PathType = ..., topdown: bool = ...,
              onerror: Optional[Callable] = ..., *, follow_symlinks: bool = ...,
              dir_fd: Optional[int] = ...) -> Iterator[Tuple[str, List[str], List[str], int]]: ...  # Unix only
    def getxattr(path: _FdOrPathType, attribute: _PathType, *, follow_symlinks: bool = ...) -> bytes: ...  # Linux only
    def listxattr(path: _FdOrPathType, *, follow_symlinks: bool = ...) -> List[str]: ...  # Linux only
    def removexattr(path: _FdOrPathType, attribute: _PathType, *, follow_symlinks: bool = ...) -> None: ...  # Linux only
    def setxattr(path: _FdOrPathType, attribute: _PathType, value: bytes, flags: int = ..., *,
                 follow_symlinks: bool = ...) -> None: ...  # Linux only

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
def execve(path: _FdOrPathType, args: _ExecVArgs, env: Mapping[str, str]) -> None: ...
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
    class _wrap_close(_TextIOWrapper):
        def close(self) -> Optional[int]: ...  # type: ignore
    def popen(command: str, mode: str = ..., buffering: int = ...) -> _wrap_close: ...
else:
    class _wrap_close(IO[Text]):
        def close(self) -> Optional[int]: ...  # type: ignore
    def popen(__cmd: Text, __mode: Text = ..., __bufsize: int = ...) -> _wrap_close: ...
    def popen2(__cmd: Text, __mode: Text = ..., __bufsize: int = ...) -> Tuple[IO[Text], IO[Text]]: ...
    def popen3(__cmd: Text, __mode: Text = ..., __bufsize: int = ...) -> Tuple[IO[Text], IO[Text], IO[Text]]: ...
    def popen4(__cmd: Text, __mode: Text = ..., __bufsize: int = ...) -> Tuple[IO[Text], IO[Text]]: ...

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
if sys.version_info >= (3, 3):
    from posix import times_result
    def times() -> times_result: ...
else:
    def times() -> Tuple[float, float, float, float, float]: ...
def wait() -> Tuple[int, int]: ...  # Unix only
if sys.version_info >= (3, 3):
    from posix import waitid_result
    def waitid(idtype: int, ident: int, options: int) -> waitid_result: ...  # Unix only
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

if sys.version_info >= (3, 3):
    from posix import sched_param
    def sched_get_priority_min(policy: int) -> int: ...  # some flavors of Unix
    def sched_get_priority_max(policy: int) -> int: ...  # some flavors of Unix
    def sched_setscheduler(pid: int, policy: int, param: sched_param) -> None: ...  # some flavors of Unix
    def sched_getscheduler(pid: int) -> int: ...  # some flavors of Unix
    def sched_setparam(pid: int, param: sched_param) -> None: ...  # some flavors of Unix
    def sched_getparam(pid: int) -> sched_param: ...  # some flavors of Unix
    def sched_rr_get_interval(pid: int) -> float: ...  # some flavors of Unix
    def sched_yield() -> None: ...  # some flavors of Unix
    def sched_setaffinity(pid: int, mask: Iterable[int]) -> None: ...  # some flavors of Unix
    def sched_getaffinity(pid: int) -> Set[int]: ...  # some flavors of Unix

def confstr(name: Union[str, int]) -> Optional[str]: ...  # Unix only
if sys.version_info >= (3, 4):
    def cpu_count() -> Optional[int]: ...
def getloadavg() -> Tuple[float, float, float]: ...  # Unix only
def sysconf(name: Union[str, int]) -> int: ...  # Unix only
if sys.version_info >= (3, 6):
    def getrandom(size: int, flags: int = ...) -> bytes: ...
    def urandom(size: int) -> bytes: ...
else:
    def urandom(n: int) -> bytes: ...
