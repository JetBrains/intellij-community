# Utility types for typeshed
#
# See the README.md file in this directory for more information.

import array
import mmap
import sys
from os import PathLike
from typing import AbstractSet, Any, Container, Iterable, Protocol, Tuple, TypeVar, Union
from typing_extensions import Literal, final

_KT = TypeVar("_KT")
_KT_co = TypeVar("_KT_co", covariant=True)
_KT_contra = TypeVar("_KT_contra", contravariant=True)
_VT = TypeVar("_VT")
_VT_co = TypeVar("_VT_co", covariant=True)
_T = TypeVar("_T")
_T_co = TypeVar("_T_co", covariant=True)
_T_contra = TypeVar("_T_contra", contravariant=True)

# stable
class IdentityFunction(Protocol):
    def __call__(self, __x: _T) -> _T: ...

class SupportsLessThan(Protocol):
    def __lt__(self, __other: Any) -> bool: ...

SupportsLessThanT = TypeVar("SupportsLessThanT", bound=SupportsLessThan)  # noqa: Y001

class SupportsDivMod(Protocol[_T_contra, _T_co]):
    def __divmod__(self, __other: _T_contra) -> _T_co: ...

class SupportsRDivMod(Protocol[_T_contra, _T_co]):
    def __rdivmod__(self, __other: _T_contra) -> _T_co: ...

# Mapping-like protocols

# stable
class SupportsItems(Protocol[_KT_co, _VT_co]):
    def items(self) -> AbstractSet[Tuple[_KT_co, _VT_co]]: ...

# stable
class SupportsKeysAndGetItem(Protocol[_KT, _VT_co]):
    def keys(self) -> Iterable[_KT]: ...
    def __getitem__(self, __k: _KT) -> _VT_co: ...

# stable
class SupportsGetItem(Container[_KT_contra], Protocol[_KT_contra, _VT_co]):
    def __getitem__(self, __k: _KT_contra) -> _VT_co: ...

# stable
class SupportsItemAccess(SupportsGetItem[_KT_contra, _VT], Protocol[_KT_contra, _VT]):
    def __setitem__(self, __k: _KT_contra, __v: _VT) -> None: ...
    def __delitem__(self, __v: _KT_contra) -> None: ...

# These aliases are simple strings in Python 2.
StrPath = Union[str, PathLike[str]]  # stable
BytesPath = Union[bytes, PathLike[bytes]]  # stable
StrOrBytesPath = Union[str, bytes, PathLike[str], PathLike[bytes]]  # stable
AnyPath = StrOrBytesPath  # obsolete, will be removed soon

OpenTextModeUpdating = Literal[
    "r+",
    "+r",
    "rt+",
    "r+t",
    "+rt",
    "tr+",
    "t+r",
    "+tr",
    "w+",
    "+w",
    "wt+",
    "w+t",
    "+wt",
    "tw+",
    "t+w",
    "+tw",
    "a+",
    "+a",
    "at+",
    "a+t",
    "+at",
    "ta+",
    "t+a",
    "+ta",
    "x+",
    "+x",
    "xt+",
    "x+t",
    "+xt",
    "tx+",
    "t+x",
    "+tx",
]
OpenTextModeWriting = Literal["w", "wt", "tw", "a", "at", "ta", "x", "xt", "tx"]
OpenTextModeReading = Literal["r", "rt", "tr", "U", "rU", "Ur", "rtU", "rUt", "Urt", "trU", "tUr", "Utr"]
OpenTextMode = Union[OpenTextModeUpdating, OpenTextModeWriting, OpenTextModeReading]
OpenBinaryModeUpdating = Literal[
    "rb+",
    "r+b",
    "+rb",
    "br+",
    "b+r",
    "+br",
    "wb+",
    "w+b",
    "+wb",
    "bw+",
    "b+w",
    "+bw",
    "ab+",
    "a+b",
    "+ab",
    "ba+",
    "b+a",
    "+ba",
    "xb+",
    "x+b",
    "+xb",
    "bx+",
    "b+x",
    "+bx",
]
OpenBinaryModeWriting = Literal["wb", "bw", "ab", "ba", "xb", "bx"]
OpenBinaryModeReading = Literal["rb", "br", "rbU", "rUb", "Urb", "brU", "bUr", "Ubr"]
OpenBinaryMode = Union[OpenBinaryModeUpdating, OpenBinaryModeReading, OpenBinaryModeWriting]

# stable
class HasFileno(Protocol):
    def fileno(self) -> int: ...

FileDescriptor = int  # stable
FileDescriptorLike = Union[int, HasFileno]  # stable

# stable
class SupportsRead(Protocol[_T_co]):
    def read(self, __length: int = ...) -> _T_co: ...

# stable
class SupportsReadline(Protocol[_T_co]):
    def readline(self, __length: int = ...) -> _T_co: ...

# stable
class SupportsNoArgReadline(Protocol[_T_co]):
    def readline(self) -> _T_co: ...

# stable
class SupportsWrite(Protocol[_T_contra]):
    def write(self, __s: _T_contra) -> Any: ...

ReadableBuffer = Union[bytes, bytearray, memoryview, array.array[Any], mmap.mmap]  # stable
WriteableBuffer = Union[bytearray, memoryview, array.array[Any], mmap.mmap]  # stable

# stable
if sys.version_info >= (3, 10):
    from types import NoneType as NoneType
else:
    # Used by type checkers for checks involving None (does not exist at runtime)
    @final
    class NoneType:
        def __bool__(self) -> Literal[False]: ...
