import abc
from collections.abc import Callable
from socket import socket
from ssl import SSLContext, SSLSocket
from typing import Any

from pika.adapters.utils.nbio_interface import (
    AbstractFileDescriptorServices,
    AbstractIOReference,
    AbstractIOServices,
    AbstractStreamProtocol,
    AbstractStreamTransport,
)
from pika.adapters.utils.selector_ioloop_adapter import _SupportsCancel

def check_callback_arg(callback: Callable[..., Any], name: str) -> None: ...
def check_fd_arg(fd: int) -> None: ...

class SocketConnectionMixin:
    def connect_socket(
        self, sock: socket, resolved_addr: tuple[str, int], on_done: Callable[[BaseException | None], None]
    ) -> _AsyncServiceAsyncHandle: ...

class StreamingConnectionMixin:
    def create_streaming_connection(
        self,
        protocol_factory: Callable[[], AbstractStreamProtocol],
        sock: socket,
        on_done: Callable[[tuple[AbstractStreamTransport, AbstractStreamProtocol] | BaseException], None],
        ssl_context: SSLContext | None = None,
        server_hostname: str | None = None,
    ) -> AbstractIOReference: ...

class _AsyncServiceAsyncHandle(AbstractIOReference):
    def __init__(self, subject: _SupportsCancel) -> None: ...
    def cancel(self) -> bool: ...

class _AsyncSocketConnector:
    def __init__(
        self,
        nbio: AbstractIOServices | AbstractFileDescriptorServices,
        sock: socket,
        resolved_addr: tuple[str, int],
        on_done: Callable[[BaseException | None], None],
    ) -> None: ...
    def start(self) -> AbstractIOReference: ...
    def cancel(self) -> bool: ...

class _AsyncStreamConnector:
    def __init__(
        self,
        nbio: AbstractIOServices | AbstractFileDescriptorServices,
        protocol_factory: Callable[[], AbstractStreamProtocol],
        sock: socket,
        ssl_context: SSLContext,
        server_hostname: str | None,
        on_done: Callable[[tuple[AbstractStreamTransport, AbstractStreamProtocol] | BaseException], None],
    ) -> None: ...
    def start(self) -> AbstractIOReference: ...
    def cancel(self) -> bool: ...

class _AsyncTransportBase(AbstractStreamTransport, metaclass=abc.ABCMeta):
    class RxEndOfFile(OSError):
        def __init__(self) -> None: ...

    def __init__(
        self,
        sock: socket | SSLSocket,
        protocol: AbstractStreamProtocol,
        nbio: AbstractIOServices | AbstractFileDescriptorServices,
    ) -> None: ...
    def abort(self) -> None: ...
    def get_protocol(self) -> AbstractStreamProtocol: ...
    def get_write_buffer_size(self) -> int: ...

class _AsyncPlaintextTransport(_AsyncTransportBase):
    def __init__(
        self,
        sock: socket | SSLSocket,
        protocol: AbstractStreamProtocol,
        nbio: AbstractIOServices | AbstractFileDescriptorServices,
    ) -> None: ...
    def write(self, data: bytes) -> None: ...

class _AsyncSSLTransport(_AsyncTransportBase):
    def __init__(
        self, sock: SSLSocket, protocol: AbstractStreamProtocol, nbio: AbstractIOServices | AbstractFileDescriptorServices
    ) -> None: ...
    def write(self, data: bytes) -> None: ...
