from _typeshed import (
    BytesPath,
    Incomplete,
    OpenBinaryMode,
    OpenBinaryModeReading,
    OpenBinaryModeUpdating,
    OpenBinaryModeWriting,
    OpenTextMode,
    StrOrBytesPath,
    StrPath,
)
from asyncio import AbstractEventLoop
from typing import AnyStr, TypeVar, overload
from typing_extensions import Literal

from ..base import AiofilesContextManager
from ..threadpool.binary import AsyncBufferedIOBase, AsyncBufferedReader, AsyncFileIO
from ..threadpool.text import AsyncTextIOWrapper
from .temptypes import AsyncTemporaryDirectory

_T_co = TypeVar("_T_co", covariant=True)
_V_co = TypeVar("_V_co", covariant=True)
_T_contra = TypeVar("_T_contra", contravariant=True)

# Text mode: always returns AsyncTextIOWrapper
@overload
def NamedTemporaryFile(
    mode: OpenTextMode,
    buffering: int = ...,
    encoding: str | None = ...,
    newline: str | None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    delete: bool = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncTextIOWrapper]: ...

# Unbuffered binary: returns a FileIO
@overload
def NamedTemporaryFile(
    mode: OpenBinaryMode,
    buffering: Literal[0],
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    delete: bool = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncFileIO]: ...

# Buffered binary reading/updating: AsyncBufferedReader
@overload
def NamedTemporaryFile(
    mode: OpenBinaryModeReading | OpenBinaryModeUpdating = ...,
    buffering: Literal[-1, 1] = ...,
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    delete: bool = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncBufferedReader]: ...

# Buffered binary writing: AsyncBufferedIOBase
@overload
def NamedTemporaryFile(
    mode: OpenBinaryModeWriting,
    buffering: Literal[-1, 1] = ...,
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    delete: bool = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncBufferedIOBase]: ...

# Text mode: always returns AsyncTextIOWrapper
@overload
def TemporaryFile(
    mode: OpenTextMode,
    buffering: int = ...,
    encoding: str | None = ...,
    newline: str | None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncTextIOWrapper]: ...

# Unbuffered binary: returns a FileIO
@overload
def TemporaryFile(
    mode: OpenBinaryMode,
    buffering: Literal[0],
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncFileIO]: ...

# Buffered binary reading/updating: AsyncBufferedReader
@overload
def TemporaryFile(
    mode: OpenBinaryModeReading | OpenBinaryModeUpdating = ...,
    buffering: Literal[-1, 1] = ...,
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncBufferedReader]: ...

# Buffered binary writing: AsyncBufferedIOBase
@overload
def TemporaryFile(
    mode: OpenBinaryModeWriting,
    buffering: Literal[-1, 1] = ...,
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncBufferedIOBase]: ...

# Text mode: always returns AsyncTextIOWrapper
@overload
def SpooledTemporaryFile(
    max_size: int = ...,
    *,
    mode: OpenTextMode,
    buffering: int = ...,
    encoding: str | None = ...,
    newline: str | None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncTextIOWrapper]: ...
@overload
def SpooledTemporaryFile(
    max_size: int,
    mode: OpenTextMode,
    buffering: int = ...,
    encoding: str | None = ...,
    newline: str | None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncTextIOWrapper]: ...

# Unbuffered binary: returns a FileIO
@overload
def SpooledTemporaryFile(
    max_size: int = ...,
    mode: OpenBinaryMode = ...,
    *,
    buffering: Literal[0],
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncFileIO]: ...
@overload
def SpooledTemporaryFile(
    max_size: int,
    mode: OpenBinaryMode,
    buffering: Literal[0],
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncFileIO]: ...

# Buffered binary reading/updating: AsyncBufferedReader
@overload
def SpooledTemporaryFile(
    max_size: int = ...,
    mode: OpenBinaryModeReading | OpenBinaryModeUpdating = ...,
    buffering: Literal[-1, 1] = ...,
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncBufferedReader]: ...

# Buffered binary writing: AsyncBufferedIOBase
@overload
def SpooledTemporaryFile(
    max_size: int = ...,
    *,
    mode: OpenBinaryModeWriting,
    buffering: Literal[-1, 1] = ...,
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncBufferedIOBase]: ...
@overload
def SpooledTemporaryFile(
    max_size: int,
    mode: OpenBinaryModeWriting,
    buffering: Literal[-1, 1] = ...,
    encoding: None = ...,
    newline: None = ...,
    suffix: AnyStr | None = ...,
    prefix: AnyStr | None = ...,
    dir: StrOrBytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManager[None, None, AsyncBufferedIOBase]: ...
@overload
def TemporaryDirectory(
    suffix: str | None = ...,
    prefix: str | None = ...,
    dir: StrPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManagerTempDir[None, None, AsyncTemporaryDirectory]: ...
@overload
def TemporaryDirectory(
    suffix: bytes | None = ...,
    prefix: bytes | None = ...,
    dir: BytesPath | None = ...,
    loop: AbstractEventLoop | None = ...,
    executor: Incomplete | None = ...,
) -> AiofilesContextManagerTempDir[None, None, AsyncTemporaryDirectory]: ...

class AiofilesContextManagerTempDir(AiofilesContextManager[_T_co, _T_contra, _V_co]):
    async def __aenter__(self) -> Incomplete: ...
