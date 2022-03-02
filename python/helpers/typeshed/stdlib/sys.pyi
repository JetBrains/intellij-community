import sys
from _typeshed import structseq
from builtins import object as _object
from importlib.abc import PathEntryFinder
from importlib.machinery import ModuleSpec
from io import TextIOWrapper
from types import FrameType, ModuleType, TracebackType
from typing import Any, AsyncGenerator, Callable, NoReturn, Optional, Protocol, Sequence, TextIO, TypeVar, Union, overload
from typing_extensions import Literal, final

_T = TypeVar("_T")

# The following type alias are stub-only and do not exist during runtime
_ExcInfo = tuple[type[BaseException], BaseException, TracebackType]
_OptExcInfo = Union[_ExcInfo, tuple[None, None, None]]

# Intentionally omits one deprecated and one optional method of `importlib.abc.MetaPathFinder`
class _MetaPathFinder(Protocol):
    def find_spec(self, fullname: str, path: Sequence[str] | None, target: ModuleType | None = ...) -> ModuleSpec | None: ...

# ----- sys variables -----
if sys.platform != "win32":
    abiflags: str
argv: list[str]
base_exec_prefix: str
base_prefix: str
byteorder: Literal["little", "big"]
builtin_module_names: Sequence[str]  # actually a tuple of strings
copyright: str
if sys.platform == "win32":
    dllhandle: int
dont_write_bytecode: bool
displayhook: Callable[[object], Any]
excepthook: Callable[[type[BaseException], BaseException, TracebackType | None], Any]
exec_prefix: str
executable: str
float_repr_style: Literal["short", "legacy"]
hexversion: int
last_type: type[BaseException] | None
last_value: BaseException | None
last_traceback: TracebackType | None
maxsize: int
maxunicode: int
meta_path: list[_MetaPathFinder]
modules: dict[str, ModuleType]
if sys.version_info >= (3, 10):
    orig_argv: list[str]
path: list[str]
path_hooks: list[Callable[[str], PathEntryFinder]]
path_importer_cache: dict[str, PathEntryFinder | None]
platform: str
if sys.version_info >= (3, 9):
    platlibdir: str
prefix: str
if sys.version_info >= (3, 8):
    pycache_prefix: str | None
ps1: object
ps2: object
stdin: TextIO
stdout: TextIO
stderr: TextIO
if sys.version_info >= (3, 10):
    stdlib_module_names: frozenset[str]
__stdin__: TextIOWrapper
__stdout__: TextIOWrapper
__stderr__: TextIOWrapper
tracebacklimit: int
version: str
api_version: int
warnoptions: Any
#  Each entry is a tuple of the form (action, message, category, module,
#    lineno)
if sys.platform == "win32":
    winver: str
_xoptions: dict[Any, Any]

# Type alias used as a mixin for structseq classes that cannot be instantiated at runtime
# This can't be represented in the type system, so we just use `structseq[Any]`
_uninstantiable_structseq = structseq[Any]

flags: _flags

if sys.version_info >= (3, 10):
    _FlagTuple = tuple[int, int, int, int, int, int, int, int, int, int, int, int, int, bool, int, int]
elif sys.version_info >= (3, 7):
    _FlagTuple = tuple[int, int, int, int, int, int, int, int, int, int, int, int, int, bool, int]
else:
    _FlagTuple = tuple[int, int, int, int, int, int, int, int, int, int, int, int, int]

@final
class _flags(_uninstantiable_structseq, _FlagTuple):
    @property
    def debug(self) -> int: ...
    @property
    def inspect(self) -> int: ...
    @property
    def interactive(self) -> int: ...
    @property
    def optimize(self) -> int: ...
    @property
    def dont_write_bytecode(self) -> int: ...
    @property
    def no_user_site(self) -> int: ...
    @property
    def no_site(self) -> int: ...
    @property
    def ignore_environment(self) -> int: ...
    @property
    def verbose(self) -> int: ...
    @property
    def bytes_warning(self) -> int: ...
    @property
    def quiet(self) -> int: ...
    @property
    def hash_randomization(self) -> int: ...
    @property
    def isolated(self) -> int: ...
    if sys.version_info >= (3, 7):
        @property
        def dev_mode(self) -> bool: ...
        @property
        def utf8_mode(self) -> int: ...
    if sys.version_info >= (3, 10):
        @property
        def warn_default_encoding(self) -> int: ...  # undocumented

float_info: _float_info

@final
class _float_info(structseq[float], tuple[float, int, int, float, int, int, int, int, float, int, int]):
    @property
    def max(self) -> float: ...  # DBL_MAX
    @property
    def max_exp(self) -> int: ...  # DBL_MAX_EXP
    @property
    def max_10_exp(self) -> int: ...  # DBL_MAX_10_EXP
    @property
    def min(self) -> float: ...  # DBL_MIN
    @property
    def min_exp(self) -> int: ...  # DBL_MIN_EXP
    @property
    def min_10_exp(self) -> int: ...  # DBL_MIN_10_EXP
    @property
    def dig(self) -> int: ...  # DBL_DIG
    @property
    def mant_dig(self) -> int: ...  # DBL_MANT_DIG
    @property
    def epsilon(self) -> float: ...  # DBL_EPSILON
    @property
    def radix(self) -> int: ...  # FLT_RADIX
    @property
    def rounds(self) -> int: ...  # FLT_ROUNDS

hash_info: _hash_info

@final
class _hash_info(structseq[Any | int], tuple[int, int, int, int, int, str, int, int, int]):
    @property
    def width(self) -> int: ...
    @property
    def modulus(self) -> int: ...
    @property
    def inf(self) -> int: ...
    @property
    def nan(self) -> int: ...
    @property
    def imag(self) -> int: ...
    @property
    def algorithm(self) -> str: ...
    @property
    def hash_bits(self) -> int: ...
    @property
    def seed_bits(self) -> int: ...
    @property
    def cutoff(self) -> int: ...  # undocumented

implementation: _implementation

class _implementation:
    name: str
    version: _version_info
    hexversion: int
    cache_tag: str
    # Define __getattr__, as the documentation states:
    # > sys.implementation may contain additional attributes specific to the Python implementation.
    # > These non-standard attributes must start with an underscore, and are not described here.
    def __getattr__(self, name: str) -> Any: ...

int_info: _int_info

@final
class _int_info(structseq[int], tuple[int, int]):
    @property
    def bits_per_digit(self) -> int: ...
    @property
    def sizeof_digit(self) -> int: ...

@final
class _version_info(_uninstantiable_structseq, tuple[int, int, int, str, int]):
    @property
    def major(self) -> int: ...
    @property
    def minor(self) -> int: ...
    @property
    def micro(self) -> int: ...
    @property
    def releaselevel(self) -> str: ...
    @property
    def serial(self) -> int: ...

version_info: _version_info

def call_tracing(__func: Callable[..., _T], __args: Any) -> _T: ...
def _clear_type_cache() -> None: ...
def _current_frames() -> dict[int, FrameType]: ...
def _getframe(__depth: int = ...) -> FrameType: ...
def _debugmallocstats() -> None: ...
def __displayhook__(value: object) -> None: ...
def __excepthook__(type_: type[BaseException], value: BaseException, traceback: TracebackType | None) -> None: ...
def exc_info() -> _OptExcInfo: ...

# sys.exit() accepts an optional argument of anything printable
def exit(__status: object = ...) -> NoReturn: ...
def getallocatedblocks() -> int: ...
def getdefaultencoding() -> str: ...

if sys.platform != "win32":
    def getdlopenflags() -> int: ...

def getfilesystemencoding() -> str: ...
def getfilesystemencodeerrors() -> str: ...
def getrefcount(__object: Any) -> int: ...
def getrecursionlimit() -> int: ...
@overload
def getsizeof(obj: object) -> int: ...
@overload
def getsizeof(obj: object, default: int) -> int: ...
def getswitchinterval() -> float: ...

_ProfileFunc = Callable[[FrameType, str, Any], Any]

def getprofile() -> _ProfileFunc | None: ...
def setprofile(profilefunc: _ProfileFunc | None) -> None: ...

_TraceFunc = Callable[[FrameType, str, Any], Optional[Callable[[FrameType, str, Any], Any]]]

def gettrace() -> _TraceFunc | None: ...
def settrace(tracefunc: _TraceFunc | None) -> None: ...

if sys.platform == "win32":
    # A tuple of length 5, even though it has more than 5 attributes.
    @final
    class _WinVersion(_uninstantiable_structseq, tuple[int, int, int, int, str]):
        @property
        def major(self) -> int: ...
        @property
        def minor(self) -> int: ...
        @property
        def build(self) -> int: ...
        @property
        def platform(self) -> int: ...
        @property
        def service_pack(self) -> str: ...
        @property
        def service_pack_minor(self) -> int: ...
        @property
        def service_pack_major(self) -> int: ...
        @property
        def suite_mask(self) -> int: ...
        @property
        def product_type(self) -> int: ...
        @property
        def platform_version(self) -> tuple[int, int, int]: ...

    def getwindowsversion() -> _WinVersion: ...

def intern(__string: str) -> str: ...
def is_finalizing() -> bool: ...

if sys.version_info >= (3, 7):
    __breakpointhook__: Any  # contains the original value of breakpointhook
    def breakpointhook(*args: Any, **kwargs: Any) -> Any: ...

if sys.platform != "win32":
    def setdlopenflags(__flags: int) -> None: ...

def setrecursionlimit(__limit: int) -> None: ...
def setswitchinterval(__interval: float) -> None: ...
def gettotalrefcount() -> int: ...  # Debug builds only

if sys.version_info < (3, 9):
    def getcheckinterval() -> int: ...  # deprecated
    def setcheckinterval(__n: int) -> None: ...  # deprecated

if sys.version_info >= (3, 8):
    # Doesn't exist at runtime, but exported in the stubs so pytest etc. can annotate their code more easily.
    class UnraisableHookArgs:
        exc_type: type[BaseException]
        exc_value: BaseException | None
        exc_traceback: TracebackType | None
        err_msg: str | None
        object: _object | None
    unraisablehook: Callable[[UnraisableHookArgs], Any]
    def addaudithook(hook: Callable[[str, tuple[Any, ...]], Any]) -> None: ...
    def audit(__event: str, *args: Any) -> None: ...

_AsyncgenHook = Optional[Callable[[AsyncGenerator[Any, Any]], None]]

@final
class _asyncgen_hooks(structseq[_AsyncgenHook], tuple[_AsyncgenHook, _AsyncgenHook]):
    @property
    def firstiter(self) -> _AsyncgenHook: ...
    @property
    def finalizer(self) -> _AsyncgenHook: ...

def get_asyncgen_hooks() -> _asyncgen_hooks: ...
def set_asyncgen_hooks(firstiter: _AsyncgenHook = ..., finalizer: _AsyncgenHook = ...) -> None: ...

if sys.version_info >= (3, 7):
    def get_coroutine_origin_tracking_depth() -> int: ...
    def set_coroutine_origin_tracking_depth(depth: int) -> None: ...
