# Stubs for sys
# Ron Murawski <ron@horizonchess.com>

# based on http://docs.python.org/3.2/library/sys.html

from typing import (
    List, Sequence, Any, Dict, Tuple, TextIO, overload, Optional, Union,
    TypeVar, Callable, Type,
)
import sys
from types import FrameType, TracebackType
from mypy_extensions import NoReturn

_T = TypeVar('_T')

# ----- sys variables -----
abiflags = ...  # type: str
argv = ...  # type: List[str]
byteorder = ...  # type: str
builtin_module_names = ...  # type: Sequence[str] # actually a tuple of strings
copyright = ...  # type: str
# dllhandle = 0  # Windows only
dont_write_bytecode: bool
__displayhook__ = ...  # type: Any # contains the original value of displayhook
__excepthook__ = ...  # type: Any  # contains the original value of excepthook
exec_prefix = ...  # type: str
executable = ...  # type: str
float_repr_style = ...  # type: str
hexversion: int
last_type = ...  # type: Any
last_value = ...  # type: Any
last_traceback = ...  # type: Any
maxsize: int
maxunicode: int
meta_path = ...  # type: List[Any]
modules = ...  # type: Dict[str, Any]
path = ...  # type: List[str]
path_hooks = ...  # type: List[Any] # TODO precise type; function, path to finder
path_importer_cache = ...  # type: Dict[str, Any] # TODO precise type
platform = ...  # type: str
prefix = ...  # type: str
ps1 = ...  # type: str
ps2 = ...  # type: str
stdin = ...  # type: TextIO
stdout = ...  # type: TextIO
stderr = ...  # type: TextIO
__stdin__ = ...  # type: TextIO
__stdout__ = ...  # type: TextIO
__stderr__ = ...  # type: TextIO
# deprecated and removed in Python 3.3:
subversion = ...  # type: Tuple[str, str, str]
tracebacklimit = 0
version = ...  # type: str
api_version = 0
warnoptions = ...  # type: Any
#  Each entry is a tuple of the form (action, message, category, module,
#    lineno)
# winver = ''  # Windows only
_xoptions = ...  # type: Dict[Any, Any]

flags = ...  # type: _flags
class _flags:
    debug = 0
    division_warning = 0
    inspect = 0
    interactive = 0
    optimize = 0
    dont_write_bytecode = 0
    no_user_site = 0
    no_site = 0
    ignore_environment = 0
    verbose = 0
    bytes_warning = 0
    quiet = 0
    hash_randomization = 0

float_info = ...  # type: _float_info
class _float_info:
    epsilon = 0.0   # DBL_EPSILON
    dig = 0         # DBL_DIG
    mant_dig = 0    # DBL_MANT_DIG
    max = 0.0       # DBL_MAX
    max_exp = 0     # DBL_MAX_EXP
    max_10_exp = 0  # DBL_MAX_10_EXP
    min = 0.0       # DBL_MIN
    min_exp = 0     # DBL_MIN_EXP
    min_10_exp = 0  # DBL_MIN_10_EXP
    radix = 0       # FLT_RADIX
    rounds = 0      # FLT_ROUNDS

hash_info = ...  # type: _hash_info
class _hash_info:
    width = 0
    modulus = 0
    inf = 0
    nan = 0
    imag = 0

int_info = ...  # type: _int_info
class _int_info:
    bits_per_digit = 0
    sizeof_digit = 0

class _version_info(Tuple[int, int, int, str, int]):
    major = 0
    minor = 0
    micro = 0
    releaselevel = ...  # type: str
    serial = 0
version_info = ...  # type: _version_info

def call_tracing(fn: Callable[..., _T], args: Any) -> _T: ...
def _clear_type_cache() -> None: ...
def _current_frames() -> Dict[int, Any]: ...
def displayhook(value: Optional[int]) -> None: ...
def excepthook(type_: Type[BaseException], value: BaseException,
               traceback: TracebackType) -> None: ...
# TODO should be a union of tuple, see mypy#1178
def exc_info() -> Tuple[Optional[Type[BaseException]],
                        Optional[BaseException],
                        Optional[TracebackType]]: ...
# sys.exit() accepts an optional argument of anything printable
def exit(arg: object = ...) -> NoReturn:
    raise SystemExit()
def getcheckinterval() -> int: ...  # deprecated
def getdefaultencoding() -> str: ...
def getdlopenflags() -> int: ...  # Unix only
def getfilesystemencoding() -> str: ...
def getrefcount(arg: Any) -> int: ...
def getrecursionlimit() -> int: ...

@overload
def getsizeof(obj: object) -> int: ...
@overload
def getsizeof(obj: object, default: int) -> int: ...

def getswitchinterval() -> float: ...

@overload
def _getframe() -> FrameType: ...
@overload
def _getframe(depth: int) -> FrameType: ...

_ProfileFunc = Callable[[FrameType, str, Any], Any]
def getprofile() -> Optional[_ProfileFunc]: ...
def setprofile(profilefunc: _ProfileFunc) -> None: ...

_TraceFunc = Callable[[FrameType, str, Any], Optional[Callable[[FrameType, str, Any], Any]]]
def gettrace() -> Optional[_TraceFunc]: ...
def settrace(tracefunc: _TraceFunc) -> None: ...

def getwindowsversion() -> Any: ...  # Windows only, TODO return type
def intern(string: str) -> str: ...

if sys.version_info >= (3, 5):
    def is_finalizing() -> bool: ...

def setcheckinterval(interval: int) -> None: ...  # deprecated
def setdlopenflags(n: int) -> None: ...  # Linux only
def setrecursionlimit(limit: int) -> None: ...
def setswitchinterval(interval: float) -> None: ...
def settscdump(on_flag: bool) -> None: ...

def gettotalrefcount() -> int: ...  # Debug builds only
