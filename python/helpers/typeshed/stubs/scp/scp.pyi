from collections.abc import Callable, Iterable
from pathlib import PurePath
from types import TracebackType
from typing import Final, Literal, Protocol, TypeAlias, type_check_only
from typing_extensions import Self

from paramiko.channel import Channel
from paramiko.transport import Transport

__version__: Final[str]

SCP_COMMAND: Final = b"scp"
PATH_TYPES: Final[tuple[type[str], type[bytes], type[PurePath]]]
bytes_sep: Final[bytes]

PathTypes: TypeAlias = str | bytes | PurePath

@type_check_only
class _PutFOReader(Protocol):
    def read(self, size: int, /) -> str | bytes | bytearray: ...
    def tell(self) -> int: ...
    def seek(self, offset: int, whence: Literal[0, 2], /) -> object: ...

def asbytes(s: PathTypes) -> bytes: ...
def asunicode(s: bytes | str) -> str: ...
def asunicode_win(s: bytes | str) -> str: ...

class SCPClient:
    transport: Transport
    buff_size: int
    socket_timeout: float | None
    channel: Channel | None
    preserve_times: bool
    sanitize: Callable[[bytes], bytes]
    peername: tuple[str, int]
    scp_command: bytes
    def __init__(
        self,
        transport: Transport,
        buff_size: int = 16384,
        socket_timeout: float | None = 10.0,
        progress: Callable[[str | bytes, int, int], None] | None = None,
        progress4: Callable[[str | bytes, int, int, tuple[str, int]], None] | None = None,
        sanitize: Callable[[bytes], bytes] | Literal[False] = ...,
        limit_bw: int | None = None,
    ) -> None: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self, type: type[BaseException] | None, value: BaseException | None, traceback: TracebackType | None
    ) -> None: ...
    def put(
        self,
        files: PathTypes | Iterable[PathTypes],
        remote_path: PathTypes = b".",
        recursive: bool = False,
        preserve_times: bool = False,
    ) -> None: ...
    def putfo(self, fl: _PutFOReader, remote_path: PathTypes, mode: str | bytes = "0644", size: int | None = None) -> None: ...
    def get(
        self,
        remote_path: PathTypes | Iterable[PathTypes],
        local_path: PathTypes = "",
        recursive: bool = False,
        preserve_times: bool = False,
    ) -> None: ...
    def close(self) -> None: ...

class SCPException(Exception): ...

def put(
    transport: Transport,
    files: PathTypes | Iterable[PathTypes],
    remote_path: PathTypes = b".",
    recursive: bool = False,
    preserve_times: bool = False,
) -> None: ...
def get(
    transport: Transport,
    remote_path: PathTypes | Iterable[PathTypes],
    local_path: PathTypes = "",
    recursive: bool = False,
    preserve_times: bool = False,
) -> None: ...
