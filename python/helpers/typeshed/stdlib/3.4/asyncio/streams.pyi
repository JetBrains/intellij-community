import sys
from typing import Any, Awaitable, Callable, Generator, Iterable, List, Optional, Tuple, Union

from . import coroutines
from . import events
from . import protocols
from . import transports

_ClientConnectedCallback = Callable[[StreamReader, StreamWriter], Optional[Awaitable[None]]]


__all__: List[str]

class IncompleteReadError(EOFError):
    expected = ...  # type: Optional[int]
    partial = ...  # type: bytes
    def __init__(self, partial: bytes, expected: Optional[int]) -> None: ...

class LimitOverrunError(Exception):
    consumed = ...  # type: int
    def __init__(self, message: str, consumed: int) -> None: ...

@coroutines.coroutine
def open_connection(
    host: str = ...,
    port: Union[int, str] = ...,
    *,
    loop: Optional[events.AbstractEventLoop] = ...,
    limit: int = ...,
    **kwds: Any
) -> Generator[Any, None, Tuple[StreamReader, StreamWriter]]: ...

@coroutines.coroutine
def start_server(
    client_connected_cb: _ClientConnectedCallback,
    host: Optional[str] = ...,
    port: Optional[Union[int, str]] = ...,
    *,
    loop: Optional[events.AbstractEventLoop] = ...,
    limit: int = ...,
    **kwds: Any
) -> Generator[Any, None, events.AbstractServer]: ...

if sys.platform != 'win32':
    @coroutines.coroutine
    def open_unix_connection(
        path: str = ...,
        *,
        loop: Optional[events.AbstractEventLoop] = ...,
        limit: int = ...,
        **kwds: Any
    ) -> Generator[Any, None, Tuple[StreamReader, StreamWriter]]: ...

    @coroutines.coroutine
    def start_unix_server(
        client_connected_cb: _ClientConnectedCallback,
        path: str = ...,
        *,
        loop: Optional[events.AbstractEventLoop] = ...,
        limit: int = ...,
        **kwds: Any) -> Generator[Any, None, events.AbstractServer]: ...

class FlowControlMixin(protocols.Protocol): ...

class StreamReaderProtocol(FlowControlMixin, protocols.Protocol):
    def __init__(self,
            stream_reader: StreamReader,
            client_connected_cb: _ClientConnectedCallback = ...,
            loop: Optional[events.AbstractEventLoop] = ...) -> None: ...
    def connection_made(self, transport: transports.BaseTransport) -> None: ...
    def connection_lost(self, exc: Exception) -> None: ...
    def data_received(self, data: bytes) -> None: ...
    def eof_received(self) -> bool: ...

class StreamWriter:
    def __init__(self,
            transport: transports.BaseTransport,
            protocol: protocols.BaseProtocol,
            reader: StreamReader,
            loop: events.AbstractEventLoop) -> None: ...
    @property
    def transport(self) -> transports.BaseTransport: ...
    def write(self, data: bytes) -> None: ...
    def writelines(self, data: Iterable[bytes]) -> None: ...
    def write_eof(self) -> None: ...
    def can_write_eof(self) -> bool: ...
    def close(self) -> None: ...
    def get_extra_info(self, name: str, default: Any = ...) -> Any: ...
    @coroutines.coroutine
    def drain(self) -> Generator[Any, None, None]: ...

class StreamReader:
    def __init__(self,
            limit: int = ...,
            loop: Optional[events.AbstractEventLoop] = ...) -> None: ...
    def exception(self) -> Exception: ...
    def set_exception(self, exc: Exception) -> None: ...
    def set_transport(self, transport: transports.BaseTransport) -> None: ...
    def feed_eof(self) -> None: ...
    def at_eof(self) -> bool: ...
    def feed_data(self, data: bytes) -> None: ...
    @coroutines.coroutine
    def readline(self) -> Generator[Any, None, bytes]: ...
    @coroutines.coroutine
    def readuntil(self, separator: bytes = ...) -> Generator[Any, None, bytes]: ...
    @coroutines.coroutine
    def read(self, n: int = ...) -> Generator[Any, None, bytes]: ...
    @coroutines.coroutine
    def readexactly(self, n: int) -> Generator[Any, None, bytes]: ...
