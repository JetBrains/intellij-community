from collections.abc import Callable, Sequence
from logging import Logger
from typing import Final

from gevent._types import _Loop, _TimerWatcher
from gevent.hub import Hub
from pika.adapters.base_connection import BaseConnection
from pika.adapters.utils.connection_workflow import AbstractAMQPConnectionWorkflow, AMQPConnectorException
from pika.adapters.utils.nbio_interface import AbstractIOReference, AbstractIOServices
from pika.adapters.utils.selector_ioloop_adapter import AbstractSelectorIOLoop, SelectorIOServicesAdapter, _SupportsCancel
from pika.connection import Connection, Parameters

LOGGER: Logger

class GeventConnection(BaseConnection[_Loop]):
    def __init__(
        self,
        parameters: Parameters | None = None,
        on_open_callback: Callable[[Connection], object] | None = None,
        on_open_error_callback: Callable[[Connection, BaseException], object] | None = None,
        on_close_callback: Callable[[Connection, BaseException], object] | None = None,
        custom_ioloop: _Loop | AbstractIOServices | None = None,
        internal_connection_workflow: bool = True,
    ) -> None: ...
    @classmethod
    def create_connection(
        cls,
        connection_configs: Sequence[Parameters],
        on_done: Callable[[Connection | AMQPConnectorException], object],
        custom_ioloop: _Loop | None = None,
        workflow: AbstractAMQPConnectionWorkflow | None = None,
    ) -> AbstractAMQPConnectionWorkflow: ...

class _TSafeCallbackQueue:
    def __init__(self) -> None: ...
    @property
    def fd(self) -> int: ...
    def add_callback_threadsafe(self, callback: Callable[[], None]) -> None: ...
    def run_next_callback(self) -> None: ...

class _GeventSelectorIOLoop(AbstractSelectorIOLoop[_TimerWatcher]):
    READ: Final[int]
    WRITE: Final[int]
    ERROR: Final[int]
    def __init__(self, gevent_hub: Hub | None = None) -> None: ...
    def close(self) -> None: ...
    def start(self) -> None: ...
    def stop(self) -> None: ...
    def add_callback(self, callback: Callable[[], object]) -> None: ...
    def call_later(self, delay: float, callback: Callable[[], object]) -> _TimerWatcher: ...
    def remove_timeout(self, timeout_handle: _TimerWatcher) -> None: ...
    def add_handler(self, fd: int, handler: Callable[[int, int], None], events: int) -> None: ...
    def update_handler(self, fd: int, events: int) -> None: ...
    def remove_handler(self, fd: int) -> None: ...

class _GeventSelectorIOServicesAdapter(SelectorIOServicesAdapter):
    def getaddrinfo(
        self,
        host: str | bytes | None,
        port: str | bytes | int | None,
        on_done: Callable[  # list is result of socket.getaddrinfo
            [list[tuple[int, int, int, str, tuple[str, int] | tuple[str, int, int, int] | tuple[int, bytes]]] | BaseException],
            None,
        ],
        family: int = 0,
        socktype: int = 0,
        proto: int = 0,
        flags: int = 0,
    ) -> AbstractIOReference: ...

class _GeventIOLoopIOHandle(AbstractIOReference):
    def __init__(self, subject: _SupportsCancel) -> None: ...
    def cancel(self) -> bool: ...

class _GeventAddressResolver:
    __slots__ = ("_loop", "_on_done", "_greenlet", "_ga_host", "_ga_port", "_ga_family", "_ga_socktype", "_ga_proto", "_ga_flags")
    def __init__(
        self,
        native_loop: AbstractSelectorIOLoop,
        host: str | bytes | None,
        port: str | bytes | int | None,
        family: int,
        socktype: int,
        proto: int,
        flags: int,
        on_done: Callable[  # list is result of socket.getaddrinfo
            [list[tuple[int, int, int, str, tuple[str, int] | tuple[str, int, int, int] | tuple[int, bytes]]] | BaseException],
            None,
        ],
    ) -> None: ...
    def start(self) -> None: ...
    def cancel(self) -> bool: ...
