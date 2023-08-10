import ssl
import sys
from _typeshed import FileDescriptorLike, WriteableBuffer
from asyncio.events import AbstractEventLoop, AbstractServer, Handle, TimerHandle, _TaskFactory
from asyncio.futures import Future
from asyncio.protocols import BaseProtocol
from asyncio.tasks import Task
from asyncio.transports import BaseTransport, ReadTransport, SubprocessTransport, WriteTransport
from collections.abc import Awaitable, Callable, Coroutine, Generator, Iterable, Sequence
from contextvars import Context
from socket import AddressFamily, SocketKind, _Address, _RetAddress, socket
from typing import IO, Any, TypeVar, overload
from typing_extensions import Literal, TypeAlias

if sys.version_info >= (3, 9):
    __all__ = ("BaseEventLoop", "Server")
else:
    __all__ = ("BaseEventLoop",)

_T = TypeVar("_T")
_ProtocolT = TypeVar("_ProtocolT", bound=BaseProtocol)
_Context: TypeAlias = dict[str, Any]
_ExceptionHandler: TypeAlias = Callable[[AbstractEventLoop, _Context], object]
_ProtocolFactory: TypeAlias = Callable[[], BaseProtocol]
_SSLContext: TypeAlias = bool | None | ssl.SSLContext

class Server(AbstractServer):
    if sys.version_info >= (3, 11):
        def __init__(
            self,
            loop: AbstractEventLoop,
            sockets: Iterable[socket],
            protocol_factory: _ProtocolFactory,
            ssl_context: _SSLContext,
            backlog: int,
            ssl_handshake_timeout: float | None,
            ssl_shutdown_timeout: float | None = ...,
        ) -> None: ...
    else:
        def __init__(
            self,
            loop: AbstractEventLoop,
            sockets: Iterable[socket],
            protocol_factory: _ProtocolFactory,
            ssl_context: _SSLContext,
            backlog: int,
            ssl_handshake_timeout: float | None,
        ) -> None: ...

    def get_loop(self) -> AbstractEventLoop: ...
    def is_serving(self) -> bool: ...
    async def start_serving(self) -> None: ...
    async def serve_forever(self) -> None: ...
    if sys.version_info >= (3, 8):
        @property
        def sockets(self) -> tuple[socket, ...]: ...
    else:
        @property
        def sockets(self) -> list[socket]: ...

    def close(self) -> None: ...
    async def wait_closed(self) -> None: ...

class BaseEventLoop(AbstractEventLoop):
    def run_forever(self) -> None: ...
    # Can't use a union, see mypy issue  # 1873.
    @overload
    def run_until_complete(self, future: Generator[Any, None, _T]) -> _T: ...
    @overload
    def run_until_complete(self, future: Awaitable[_T]) -> _T: ...
    def stop(self) -> None: ...
    def is_running(self) -> bool: ...
    def is_closed(self) -> bool: ...
    def close(self) -> None: ...
    async def shutdown_asyncgens(self) -> None: ...
    # Methods scheduling callbacks.  All these return Handles.
    def call_soon(self, callback: Callable[..., object], *args: Any, context: Context | None = ...) -> Handle: ...
    def call_later(
        self, delay: float, callback: Callable[..., object], *args: Any, context: Context | None = ...
    ) -> TimerHandle: ...
    def call_at(self, when: float, callback: Callable[..., object], *args: Any, context: Context | None = ...) -> TimerHandle: ...
    def time(self) -> float: ...
    # Future methods
    def create_future(self) -> Future[Any]: ...
    # Tasks methods
    if sys.version_info >= (3, 11):
        def create_task(
            self, coro: Coroutine[Any, Any, _T] | Generator[Any, None, _T], *, name: object = ..., context: Context | None = ...
        ) -> Task[_T]: ...
    elif sys.version_info >= (3, 8):
        def create_task(self, coro: Coroutine[Any, Any, _T] | Generator[Any, None, _T], *, name: object = ...) -> Task[_T]: ...
    else:
        def create_task(self, coro: Coroutine[Any, Any, _T] | Generator[Any, None, _T]) -> Task[_T]: ...

    def set_task_factory(self, factory: _TaskFactory | None) -> None: ...
    def get_task_factory(self) -> _TaskFactory | None: ...
    # Methods for interacting with threads
    def call_soon_threadsafe(self, callback: Callable[..., object], *args: Any, context: Context | None = ...) -> Handle: ...
    def run_in_executor(self, executor: Any, func: Callable[..., _T], *args: Any) -> Future[_T]: ...
    def set_default_executor(self, executor: Any) -> None: ...
    # Network I/O methods returning Futures.
    async def getaddrinfo(
        self,
        host: bytes | str | None,
        port: str | int | None,
        *,
        family: int = ...,
        type: int = ...,
        proto: int = ...,
        flags: int = ...,
    ) -> list[tuple[AddressFamily, SocketKind, int, str, tuple[str, int] | tuple[str, int, int, int]]]: ...
    async def getnameinfo(self, sockaddr: tuple[str, int] | tuple[str, int, int, int], flags: int = ...) -> tuple[str, str]: ...
    if sys.version_info >= (3, 11):
        @overload
        async def create_connection(
            self,
            protocol_factory: Callable[[], _ProtocolT],
            host: str = ...,
            port: int = ...,
            *,
            ssl: _SSLContext = ...,
            family: int = ...,
            proto: int = ...,
            flags: int = ...,
            sock: None = ...,
            local_addr: tuple[str, int] | None = ...,
            server_hostname: str | None = ...,
            ssl_handshake_timeout: float | None = ...,
            ssl_shutdown_timeout: float | None = ...,
            happy_eyeballs_delay: float | None = ...,
            interleave: int | None = ...,
        ) -> tuple[BaseTransport, _ProtocolT]: ...
        @overload
        async def create_connection(
            self,
            protocol_factory: Callable[[], _ProtocolT],
            host: None = ...,
            port: None = ...,
            *,
            ssl: _SSLContext = ...,
            family: int = ...,
            proto: int = ...,
            flags: int = ...,
            sock: socket,
            local_addr: None = ...,
            server_hostname: str | None = ...,
            ssl_handshake_timeout: float | None = ...,
            ssl_shutdown_timeout: float | None = ...,
            happy_eyeballs_delay: float | None = ...,
            interleave: int | None = ...,
        ) -> tuple[BaseTransport, _ProtocolT]: ...
    elif sys.version_info >= (3, 8):
        @overload
        async def create_connection(
            self,
            protocol_factory: Callable[[], _ProtocolT],
            host: str = ...,
            port: int = ...,
            *,
            ssl: _SSLContext = ...,
            family: int = ...,
            proto: int = ...,
            flags: int = ...,
            sock: None = ...,
            local_addr: tuple[str, int] | None = ...,
            server_hostname: str | None = ...,
            ssl_handshake_timeout: float | None = ...,
            happy_eyeballs_delay: float | None = ...,
            interleave: int | None = ...,
        ) -> tuple[BaseTransport, _ProtocolT]: ...
        @overload
        async def create_connection(
            self,
            protocol_factory: Callable[[], _ProtocolT],
            host: None = ...,
            port: None = ...,
            *,
            ssl: _SSLContext = ...,
            family: int = ...,
            proto: int = ...,
            flags: int = ...,
            sock: socket,
            local_addr: None = ...,
            server_hostname: str | None = ...,
            ssl_handshake_timeout: float | None = ...,
            happy_eyeballs_delay: float | None = ...,
            interleave: int | None = ...,
        ) -> tuple[BaseTransport, _ProtocolT]: ...
    else:
        @overload
        async def create_connection(
            self,
            protocol_factory: Callable[[], _ProtocolT],
            host: str = ...,
            port: int = ...,
            *,
            ssl: _SSLContext = ...,
            family: int = ...,
            proto: int = ...,
            flags: int = ...,
            sock: None = ...,
            local_addr: tuple[str, int] | None = ...,
            server_hostname: str | None = ...,
            ssl_handshake_timeout: float | None = ...,
        ) -> tuple[BaseTransport, _ProtocolT]: ...
        @overload
        async def create_connection(
            self,
            protocol_factory: Callable[[], _ProtocolT],
            host: None = ...,
            port: None = ...,
            *,
            ssl: _SSLContext = ...,
            family: int = ...,
            proto: int = ...,
            flags: int = ...,
            sock: socket,
            local_addr: None = ...,
            server_hostname: str | None = ...,
            ssl_handshake_timeout: float | None = ...,
        ) -> tuple[BaseTransport, _ProtocolT]: ...
    if sys.version_info >= (3, 11):
        @overload
        async def create_server(
            self,
            protocol_factory: _ProtocolFactory,
            host: str | Sequence[str] | None = ...,
            port: int = ...,
            *,
            family: int = ...,
            flags: int = ...,
            sock: None = ...,
            backlog: int = ...,
            ssl: _SSLContext = ...,
            reuse_address: bool | None = ...,
            reuse_port: bool | None = ...,
            ssl_handshake_timeout: float | None = ...,
            ssl_shutdown_timeout: float | None = ...,
            start_serving: bool = ...,
        ) -> Server: ...
        @overload
        async def create_server(
            self,
            protocol_factory: _ProtocolFactory,
            host: None = ...,
            port: None = ...,
            *,
            family: int = ...,
            flags: int = ...,
            sock: socket = ...,
            backlog: int = ...,
            ssl: _SSLContext = ...,
            reuse_address: bool | None = ...,
            reuse_port: bool | None = ...,
            ssl_handshake_timeout: float | None = ...,
            ssl_shutdown_timeout: float | None = ...,
            start_serving: bool = ...,
        ) -> Server: ...
        async def start_tls(
            self,
            transport: BaseTransport,
            protocol: BaseProtocol,
            sslcontext: ssl.SSLContext,
            *,
            server_side: bool = ...,
            server_hostname: str | None = ...,
            ssl_handshake_timeout: float | None = ...,
            ssl_shutdown_timeout: float | None = ...,
        ) -> BaseTransport: ...
        async def connect_accepted_socket(
            self,
            protocol_factory: Callable[[], _ProtocolT],
            sock: socket,
            *,
            ssl: _SSLContext = ...,
            ssl_handshake_timeout: float | None = ...,
            ssl_shutdown_timeout: float | None = ...,
        ) -> tuple[BaseTransport, _ProtocolT]: ...
    else:
        @overload
        async def create_server(
            self,
            protocol_factory: _ProtocolFactory,
            host: str | Sequence[str] | None = ...,
            port: int = ...,
            *,
            family: int = ...,
            flags: int = ...,
            sock: None = ...,
            backlog: int = ...,
            ssl: _SSLContext = ...,
            reuse_address: bool | None = ...,
            reuse_port: bool | None = ...,
            ssl_handshake_timeout: float | None = ...,
            start_serving: bool = ...,
        ) -> Server: ...
        @overload
        async def create_server(
            self,
            protocol_factory: _ProtocolFactory,
            host: None = ...,
            port: None = ...,
            *,
            family: int = ...,
            flags: int = ...,
            sock: socket = ...,
            backlog: int = ...,
            ssl: _SSLContext = ...,
            reuse_address: bool | None = ...,
            reuse_port: bool | None = ...,
            ssl_handshake_timeout: float | None = ...,
            start_serving: bool = ...,
        ) -> Server: ...
        async def start_tls(
            self,
            transport: BaseTransport,
            protocol: BaseProtocol,
            sslcontext: ssl.SSLContext,
            *,
            server_side: bool = ...,
            server_hostname: str | None = ...,
            ssl_handshake_timeout: float | None = ...,
        ) -> BaseTransport: ...
        async def connect_accepted_socket(
            self,
            protocol_factory: Callable[[], _ProtocolT],
            sock: socket,
            *,
            ssl: _SSLContext = ...,
            ssl_handshake_timeout: float | None = ...,
        ) -> tuple[BaseTransport, _ProtocolT]: ...

    async def sock_sendfile(
        self, sock: socket, file: IO[bytes], offset: int = ..., count: int | None = ..., *, fallback: bool | None = ...
    ) -> int: ...
    async def sendfile(
        self, transport: BaseTransport, file: IO[bytes], offset: int = ..., count: int | None = ..., *, fallback: bool = ...
    ) -> int: ...
    if sys.version_info >= (3, 11):
        async def create_datagram_endpoint(  # type: ignore[override]
            self,
            protocol_factory: Callable[[], _ProtocolT],
            local_addr: tuple[str, int] | None = ...,
            remote_addr: tuple[str, int] | None = ...,
            *,
            family: int = ...,
            proto: int = ...,
            flags: int = ...,
            reuse_port: bool | None = ...,
            allow_broadcast: bool | None = ...,
            sock: socket | None = ...,
        ) -> tuple[BaseTransport, _ProtocolT]: ...
    else:
        async def create_datagram_endpoint(
            self,
            protocol_factory: Callable[[], _ProtocolT],
            local_addr: tuple[str, int] | None = ...,
            remote_addr: tuple[str, int] | None = ...,
            *,
            family: int = ...,
            proto: int = ...,
            flags: int = ...,
            reuse_address: bool | None = ...,
            reuse_port: bool | None = ...,
            allow_broadcast: bool | None = ...,
            sock: socket | None = ...,
        ) -> tuple[BaseTransport, _ProtocolT]: ...
    # Pipes and subprocesses.
    async def connect_read_pipe(
        self, protocol_factory: Callable[[], _ProtocolT], pipe: Any
    ) -> tuple[ReadTransport, _ProtocolT]: ...
    async def connect_write_pipe(
        self, protocol_factory: Callable[[], _ProtocolT], pipe: Any
    ) -> tuple[WriteTransport, _ProtocolT]: ...
    async def subprocess_shell(
        self,
        protocol_factory: Callable[[], _ProtocolT],
        cmd: bytes | str,
        *,
        stdin: int | IO[Any] | None = ...,
        stdout: int | IO[Any] | None = ...,
        stderr: int | IO[Any] | None = ...,
        universal_newlines: Literal[False] = ...,
        shell: Literal[True] = ...,
        bufsize: Literal[0] = ...,
        encoding: None = ...,
        errors: None = ...,
        text: Literal[False, None] = ...,
        **kwargs: Any,
    ) -> tuple[SubprocessTransport, _ProtocolT]: ...
    async def subprocess_exec(
        self,
        protocol_factory: Callable[[], _ProtocolT],
        program: Any,
        *args: Any,
        stdin: int | IO[Any] | None = ...,
        stdout: int | IO[Any] | None = ...,
        stderr: int | IO[Any] | None = ...,
        universal_newlines: Literal[False] = ...,
        shell: Literal[True] = ...,
        bufsize: Literal[0] = ...,
        encoding: None = ...,
        errors: None = ...,
        **kwargs: Any,
    ) -> tuple[SubprocessTransport, _ProtocolT]: ...
    def add_reader(self, fd: FileDescriptorLike, callback: Callable[..., Any], *args: Any) -> None: ...
    def remove_reader(self, fd: FileDescriptorLike) -> bool: ...
    def add_writer(self, fd: FileDescriptorLike, callback: Callable[..., Any], *args: Any) -> None: ...
    def remove_writer(self, fd: FileDescriptorLike) -> bool: ...
    # The sock_* methods (and probably some others) are not actually implemented on
    # BaseEventLoop, only on subclasses. We list them here for now for convenience.
    async def sock_recv(self, sock: socket, nbytes: int) -> bytes: ...
    async def sock_recv_into(self, sock: socket, buf: WriteableBuffer) -> int: ...
    async def sock_sendall(self, sock: socket, data: bytes) -> None: ...
    async def sock_connect(self, sock: socket, address: _Address) -> None: ...
    async def sock_accept(self, sock: socket) -> tuple[socket, _RetAddress]: ...
    if sys.version_info >= (3, 11):
        async def sock_recvfrom(self, sock: socket, bufsize: int) -> bytes: ...
        async def sock_recvfrom_into(self, sock: socket, buf: WriteableBuffer, nbytes: int = ...) -> int: ...
        async def sock_sendto(self, sock: socket, data: bytes, address: _Address) -> None: ...
    # Signal handling.
    def add_signal_handler(self, sig: int, callback: Callable[..., Any], *args: Any) -> None: ...
    def remove_signal_handler(self, sig: int) -> bool: ...
    # Error handlers.
    def set_exception_handler(self, handler: _ExceptionHandler | None) -> None: ...
    def get_exception_handler(self) -> _ExceptionHandler | None: ...
    def default_exception_handler(self, context: _Context) -> None: ...
    def call_exception_handler(self, context: _Context) -> None: ...
    # Debug flag management.
    def get_debug(self) -> bool: ...
    def set_debug(self, enabled: bool) -> None: ...
    if sys.version_info >= (3, 9):
        async def shutdown_default_executor(self) -> None: ...
