"""Stubs for the 'sys' module."""

from typing import (
    IO, Union, List, Sequence, Any, Dict, Tuple, BinaryIO, Optional, Callable,
    overload, Type,
)
from types import FrameType, ModuleType, TracebackType, ClassType
from mypy_extensions import NoReturn

class _flags:
    bytes_warning = ...  # type: int
    debug = ...  # type: int
    division_new = ...  # type: int
    division_warning = ...  # type: int
    dont_write_bytecode = ...  # type: int
    hash_randomization = ...  # type: int
    ignore_environment = ...  # type: int
    inspect = ...  # type: int
    interactive = ...  # type: int
    no_site = ...  # type: int
    no_user_site = ...  # type: int
    optimize = ...  # type: int
    py3k_warning = ...  # type: int
    tabcheck = ...  # type: int
    unicode = ...  # type: int
    verbose = ...  # type: int

class _float_info:
    max = ...  # type: float
    max_exp = ...  # type: int
    max_10_exp = ...  # type: int
    min = ...  # type: float
    min_exp = ...  # type: int
    min_10_exp = ...  # type: int
    dig = ...  # type: int
    mant_dig = ...  # type: int
    epsilon = ...  # type: float
    radix = ...  # type: int
    rounds = ...  # type: int

class _version_info(Tuple[int, int, int, str, int]):
    major = 0
    minor = 0
    micro = 0
    releaselevel = ...  # type: str
    serial = 0

_mercurial = ...  # type: Tuple[str, str, str]
api_version = ...  # type: int
argv = ...  # type: List[str]
builtin_module_names = ...  # type: Tuple[str, ...]
byteorder = ...  # type: str
copyright = ...  # type: str
dont_write_bytecode = ...  # type: bool
exec_prefix = ...  # type: str
executable = ...  # type: str
flags = ...  # type: _flags
float_repr_style = ...  # type: str
hexversion = ...  # type: int
long_info = ...  # type: object
maxint = ...  # type: int
maxsize = ...  # type: int
maxunicode = ...  # type: int
modules = ...  # type: Dict[str, Any]
path = ...  # type: List[str]
platform = ...  # type: str
prefix = ...  # type: str
py3kwarning = ...  # type: bool
__stderr__ = ...  # type: IO[str]
__stdin__ = ...  # type: IO[str]
__stdout__ = ...  # type: IO[str]
stderr = ...  # type: IO[str]
stdin = ...  # type: IO[str]
stdout = ...  # type: IO[str]
subversion = ...  # type: Tuple[str, str, str]
version = ...  # type: str
warnoptions = ...  # type: object
float_info = ...  # type: _float_info
version_info = ...  # type: _version_info
ps1 = ...  # type: str
ps2 = ...  # type: str
last_type = ...  # type: type
last_value = ...  # type: BaseException
last_traceback = ...  # type: TracebackType
# TODO precise types
meta_path = ...  # type: List[Any]
path_hooks = ...  # type: List[Any]
path_importer_cache = ...  # type: Dict[str, Any]
displayhook = ...  # type: Optional[Callable[[int], None]]
excepthook = ...  # type: Optional[Callable[[type, BaseException, TracebackType], None]]
exc_type = ...  # type: Optional[type]
exc_value = ...  # type: Union[BaseException, ClassType]
exc_traceback = ...  # type: TracebackType

class _WindowsVersionType:
    major = ...  # type: Any
    minor = ...  # type: Any
    build = ...  # type: Any
    platform = ...  # type: Any
    service_pack = ...  # type: Any
    service_pack_major = ...  # type: Any
    service_pack_minor = ...  # type: Any
    suite_mask = ...  # type: Any
    product_type = ...  # type: Any

def getwindowsversion() -> _WindowsVersionType: ...

def _clear_type_cache() -> None: ...
def _current_frames() -> Dict[int, FrameType]: ...
def _getframe(depth: int = ...) -> FrameType: ...
def call_tracing(fn: Any, args: Any) -> Any: ...
def __displayhook__(value: int) -> None: ...
def __excepthook__(type_: type, value: BaseException, traceback: TracebackType) -> None: ...
def exc_clear() -> None:
    raise DeprecationWarning()
# TODO should be a union of tuple, see mypy#1178
def exc_info() -> Tuple[Optional[Type[BaseException]],
                        Optional[BaseException],
                        Optional[TracebackType]]: ...

# sys.exit() accepts an optional argument of anything printable
def exit(arg: Any = ...) -> NoReturn:
    raise SystemExit()
def getcheckinterval() -> int: ...  # deprecated
def getdefaultencoding() -> str: ...
def getdlopenflags() -> int: ...
def getfilesystemencoding() -> str: ...  # In practice, never returns None
def getrefcount(arg: Any) -> int: ...
def getrecursionlimit() -> int: ...
def getsizeof(obj: object, default: int = ...) -> int: ...
def getprofile() -> None: ...
def gettrace() -> None: ...
def setcheckinterval(interval: int) -> None: ...  # deprecated
def setdlopenflags(n: int) -> None: ...
def setprofile(profilefunc: Any) -> None: ...  # TODO type
def setrecursionlimit(limit: int) -> None: ...
def settrace(tracefunc: Any) -> None: ...  # TODO type
