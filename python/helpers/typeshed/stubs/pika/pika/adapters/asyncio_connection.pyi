from _typeshed import Incomplete
from asyncio import AbstractEventLoop, Future, Handle
from collections.abc import Callable, Sequence
from logging import Logger
from typing_extensions import Self

from ..connection import Connection, Parameters
from .base_connection import BaseConnection
from .utils import io_services_utils
from .utils.connection_workflow import AbstractAMQPConnectionWorkflow, AMQPConnectorException
from .utils.nbio_interface import AbstractFileDescriptorServices, AbstractIOReference, AbstractIOServices, AbstractTimerReference

LOGGER: Logger

class AsyncioConnection(BaseConnection):
    def __init__(
        self,
        parameters: Parameters | None = None,
        on_open_callback: Callable[[Self], object] | None = None,
        on_open_error_callback: Callable[[Self, BaseException], object] | None = None,
        on_close_callback: Callable[[Self, BaseException], object] | None = None,
        custom_ioloop: AbstractEventLoop | None = None,
        internal_connection_workflow: bool = True,
    ) -> None: ...
    @classmethod
    def create_connection(
        cls,
        connection_configs: Sequence[Parameters],
        on_done: Callable[[Connection | AMQPConnectorException], object],
        custom_ioloop: AbstractEventLoop | None = None,
        workflow: AbstractAMQPConnectionWorkflow | None = None,
    ) -> AbstractAMQPConnectionWorkflow: ...

class _AsyncioIOServicesAdapter(
    io_services_utils.SocketConnectionMixin,
    io_services_utils.StreamingConnectionMixin,
    AbstractIOServices,
    AbstractFileDescriptorServices,
):
    def __init__(self, loop: AbstractEventLoop | None = None) -> None: ...
    def get_native_ioloop(self) -> AbstractEventLoop: ...
    def close(self) -> None: ...
    def run(self) -> None: ...
    def stop(self) -> None: ...
    def add_callback_threadsafe(self, callback: Callable[[], object]) -> None: ...
    def call_later(self, delay: float, callback: Callable[[], object]) -> _TimerHandle: ...
    def getaddrinfo(
        self,
        host: str,
        port: int,
        on_done: Callable[..., object],
        family: int = 0,
        socktype: int = 0,
        proto: int = 0,
        flags: int = 0,
    ) -> AbstractIOReference: ...
    def set_reader(self, fd: int, on_readable: Callable[[], object]) -> None: ...
    def remove_reader(self, fd: int) -> bool: ...
    def set_writer(self, fd: int, on_writable: Callable[[], object]) -> None: ...
    def remove_writer(self, fd: int) -> bool: ...

class _TimerHandle(AbstractTimerReference):
    def __init__(self, handle: Handle) -> None: ...
    def cancel(self) -> None: ...

class _AsyncioIOReference(AbstractIOReference):
    def __init__(self, future: Future[Incomplete], on_done: Callable[[BaseConnection | BaseException], object]) -> None: ...
    def cancel(self) -> bool: ...
