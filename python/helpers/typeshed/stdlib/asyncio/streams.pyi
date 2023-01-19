import ssl
import sys
from _typeshed import Self, StrPath
from collections.abc import AsyncIterator, Awaitable, Callable, Iterable, Sequence
from typing import Any
from typing_extensions import TypeAlias

from . import events, protocols, transports
from .base_events import Server

if sys.platform == "win32":
    if sys.version_info >= (3, 8):
        __all__ = ("StreamReader", "StreamWriter", "StreamReaderProtocol", "open_connection", "start_server")
    else:
        __all__ = (
            "StreamReader",
            "StreamWriter",
            "StreamReaderProtocol",
            "open_connection",
            "start_server",
            "IncompleteReadError",
            "LimitOverrunError",
        )
else:
    if sys.version_info >= (3, 8):
        __all__ = (
            "StreamReader",
            "StreamWriter",
            "StreamReaderProtocol",
            "open_connection",
            "start_server",
            "open_unix_connection",
            "start_unix_server",
        )
    else:
        __all__ = (
            "StreamReader",
            "StreamWriter",
            "StreamReaderProtocol",
            "open_connection",
            "start_server",
            "IncompleteReadError",
            "LimitOverrunError",
            "open_unix_connection",
            "start_unix_server",
        )

_ClientConnectedCallback: TypeAlias = Callable[[StreamReader, StreamWriter], Awaitable[None] | None]

if sys.version_info < (3, 8):
    class IncompleteReadError(EOFError):
        expected: int | None
        partial: bytes
        def __init__(self, partial: bytes, expected: int | None) -> None: ...

    class LimitOverrunError(Exception):
        consumed: int
        def __init__(self, message: str, consumed: int) -> None: ...

if sys.version_info >= (3, 10):
    async def open_connection(
        host: str | None = ...,
        port: int | str | None = ...,
        *,
        limit: int = ...,
        ssl_handshake_timeout: float | None = ...,
        **kwds: Any,
    ) -> tuple[StreamReader, StreamWriter]: ...
    async def start_server(
        client_connected_cb: _ClientConnectedCallback,
        host: str | Sequence[str] | None = ...,
        port: int | str | None = ...,
        *,
        limit: int = ...,
        ssl_handshake_timeout: float | None = ...,
        **kwds: Any,
    ) -> Server: ...

else:
    async def open_connection(
        host: str | None = ...,
        port: int | str | None = ...,
        *,
        loop: events.AbstractEventLoop | None = ...,
        limit: int = ...,
        ssl_handshake_timeout: float | None = ...,
        **kwds: Any,
    ) -> tuple[StreamReader, StreamWriter]: ...
    async def start_server(
        client_connected_cb: _ClientConnectedCallback,
        host: str | None = ...,
        port: int | str | None = ...,
        *,
        loop: events.AbstractEventLoop | None = ...,
        limit: int = ...,
        ssl_handshake_timeout: float | None = ...,
        **kwds: Any,
    ) -> Server: ...

if sys.platform != "win32":
    if sys.version_info >= (3, 10):
        async def open_unix_connection(
            path: StrPath | None = ..., *, limit: int = ..., **kwds: Any
        ) -> tuple[StreamReader, StreamWriter]: ...
        async def start_unix_server(
            client_connected_cb: _ClientConnectedCallback, path: StrPath | None = ..., *, limit: int = ..., **kwds: Any
        ) -> Server: ...
    else:
        async def open_unix_connection(
            path: StrPath | None = ..., *, loop: events.AbstractEventLoop | None = ..., limit: int = ..., **kwds: Any
        ) -> tuple[StreamReader, StreamWriter]: ...
        async def start_unix_server(
            client_connected_cb: _ClientConnectedCallback,
            path: StrPath | None = ...,
            *,
            loop: events.AbstractEventLoop | None = ...,
            limit: int = ...,
            **kwds: Any,
        ) -> Server: ...

class FlowControlMixin(protocols.Protocol):
    def __init__(self, loop: events.AbstractEventLoop | None = ...) -> None: ...

class StreamReaderProtocol(FlowControlMixin, protocols.Protocol):
    def __init__(
        self,
        stream_reader: StreamReader,
        client_connected_cb: _ClientConnectedCallback | None = ...,
        loop: events.AbstractEventLoop | None = ...,
    ) -> None: ...
    def connection_made(self, transport: transports.BaseTransport) -> None: ...
    def connection_lost(self, exc: Exception | None) -> None: ...
    def data_received(self, data: bytes) -> None: ...
    def eof_received(self) -> bool: ...

class StreamWriter:
    def __init__(
        self,
        transport: transports.WriteTransport,
        protocol: protocols.BaseProtocol,
        reader: StreamReader | None,
        loop: events.AbstractEventLoop,
    ) -> None: ...
    @property
    def transport(self) -> transports.WriteTransport: ...
    def write(self, data: bytes) -> None: ...
    def writelines(self, data: Iterable[bytes]) -> None: ...
    def write_eof(self) -> None: ...
    def can_write_eof(self) -> bool: ...
    def close(self) -> None: ...
    def is_closing(self) -> bool: ...
    async def wait_closed(self) -> None: ...
    def get_extra_info(self, name: str, default: Any = ...) -> Any: ...
    async def drain(self) -> None: ...
    if sys.version_info >= (3, 11):
        async def start_tls(
            self, sslcontext: ssl.SSLContext, *, server_hostname: str | None = ..., ssl_handshake_timeout: float | None = ...
        ) -> None: ...

class StreamReader(AsyncIterator[bytes]):
    def __init__(self, limit: int = ..., loop: events.AbstractEventLoop | None = ...) -> None: ...
    def exception(self) -> Exception: ...
    def set_exception(self, exc: Exception) -> None: ...
    def set_transport(self, transport: transports.BaseTransport) -> None: ...
    def feed_eof(self) -> None: ...
    def at_eof(self) -> bool: ...
    def feed_data(self, data: bytes) -> None: ...
    async def readline(self) -> bytes: ...
    async def readuntil(self, separator: bytes = ...) -> bytes: ...
    async def read(self, n: int = ...) -> bytes: ...
    async def readexactly(self, n: int) -> bytes: ...
    def __aiter__(self: Self) -> Self: ...
    async def __anext__(self) -> bytes: ...
