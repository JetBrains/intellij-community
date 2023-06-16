# https://pyinstaller.org/en/stable/hooks.html#module-PyInstaller.compat
from _typeshed import FileDescriptor, GenericPath, StrOrBytesPath
from collections.abc import Iterable
from importlib.abc import _Path
from types import ModuleType
from typing import AnyStr, overload
from typing_extensions import Literal, TypeAlias

_OpenFile: TypeAlias = StrOrBytesPath | FileDescriptor

is_64bits: bool
is_py35: bool
is_py36: bool
is_py37: bool
is_py38: bool
is_py39: bool
is_py310: bool
is_win: bool
is_win_10: bool
is_win_wine: bool
is_cygwin: bool
is_darwin: bool
is_linux: bool
is_solar: bool
is_aix: bool
is_freebsd: bool
is_openbsd: bool
is_hpux: bool
is_unix: bool
is_musl: bool
is_macos_11_compat: bool
is_macos_11_native: bool
is_macos_11: bool
PYDYLIB_NAMES: set[str]
base_prefix: str
is_venv: bool
is_virtualenv: bool
is_conda: bool
is_pure_conda: bool
python_executable: str
is_ms_app_store: bool
BYTECODE_MAGIC: bytes
EXTENSION_SUFFIXES: list[str]
ALL_SUFFIXES: list[str]

architecture: Literal["64bit", "n32bit", "32bit"]
system: Literal["Cygwin", "Linux", "Darwin", "Java", "Windows"]
machine: Literal["sw_64", "loongarch64", "arm", "intel", "ppc", "mips", "riscv", "s390x", "unknown", None]

def is_wine_dll(filename: _OpenFile) -> bool: ...
@overload
def getenv(name: str, default: str) -> str: ...
@overload
def getenv(name: str, default: None = ...) -> str | None: ...
def setenv(name: str, value: str) -> None: ...
def unsetenv(name: str) -> None: ...
def exec_command(
    *cmdargs: str, encoding: str | None = ..., raise_enoent: bool | None = ..., **kwargs: int | bool | Iterable[int] | None
) -> str: ...
def exec_command_rc(*cmdargs: str, **kwargs: float | bool | Iterable[int] | None) -> int: ...
def exec_command_stdout(
    *command_args: str, encoding: str | None = ..., **kwargs: float | str | bytes | bool | Iterable[int] | None
) -> str: ...
def exec_command_all(
    *cmdargs: str, encoding: str | None = ..., **kwargs: int | bool | Iterable[int] | None
) -> tuple[int, str, str]: ...
def exec_python(*args: str, **kwargs: str | None) -> str: ...
def exec_python_rc(*args: str, **kwargs: str | None) -> int: ...
def expand_path(path: GenericPath[AnyStr]) -> AnyStr: ...
def getsitepackages(prefixes: Iterable[str] | None = ...) -> list[str]: ...
def importlib_load_source(name: str, pathname: _Path) -> ModuleType: ...

PY3_BASE_MODULES: set[str]
PURE_PYTHON_MODULE_TYPES: set[str]
SPECIAL_MODULE_TYPES: set[str]
BINARY_MODULE_TYPES: set[str]
VALID_MODULE_TYPES: set[str]
BAD_MODULE_TYPES: set[str]
ALL_MODULE_TYPES: set[str]
MODULE_TYPES_TO_TOC_DICT: dict[str, str]

def check_requirements() -> None: ...
