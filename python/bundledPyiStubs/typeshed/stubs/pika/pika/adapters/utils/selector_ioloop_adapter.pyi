import abc
from collections.abc import Callable
from logging import Logger
from typing import Final, Generic, Protocol, TypeVar, type_check_only

from pika.adapters.utils import io_services_utils, nbio_interface

LOGGER: Logger

_Timeout = TypeVar("_Timeout", bound=object, default=object)

@type_check_only
class _SupportsCancel(Protocol):
    def cancel(self) -> bool: ...

class AbstractSelectorIOLoop(Generic[_Timeout], metaclass=abc.ABCMeta):
    @property
    @abc.abstractmethod
    def READ(self) -> int: ...
    @property
    @abc.abstractmethod
    def WRITE(self) -> int: ...
    @property
    @abc.abstractmethod
    def ERROR(self) -> int: ...
    @abc.abstractmethod
    def close(self) -> None: ...
    @abc.abstractmethod
    def start(self) -> None: ...
    @abc.abstractmethod
    def stop(self) -> None: ...
    @abc.abstractmethod
    def call_later(self, delay: float, callback: Callable[[], object]) -> _Timeout: ...
    @abc.abstractmethod
    def remove_timeout(self, timeout_handle: _Timeout) -> None: ...
    @abc.abstractmethod
    def add_callback(self, callback: Callable[[], object]) -> None: ...
    @abc.abstractmethod
    def add_handler(self, fd: int, handler: Callable[[int, int], None], events: int) -> None: ...
    @abc.abstractmethod
    def update_handler(self, fd: int, events: int) -> None: ...
    @abc.abstractmethod
    def remove_handler(self, fd: int) -> None: ...

class SelectorIOServicesAdapter(
    io_services_utils.SocketConnectionMixin,
    io_services_utils.StreamingConnectionMixin,
    nbio_interface.AbstractIOServices,
    nbio_interface.AbstractFileDescriptorServices,
    Generic[_Timeout],
):
    def __init__(self, native_loop: AbstractSelectorIOLoop[_Timeout]) -> None: ...
    def get_native_ioloop(self) -> AbstractSelectorIOLoop[_Timeout]: ...
    def close(self) -> None: ...
    def run(self) -> None: ...
    def stop(self) -> None: ...
    def add_callback_threadsafe(self, callback: Callable[[], None]) -> None: ...
    def call_later(self, delay: float, callback: Callable[[], None]) -> _TimerHandle: ...
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
    ) -> nbio_interface.AbstractIOReference: ...
    def set_reader(self, fd: int, on_readable: Callable[[], None]) -> None: ...
    def remove_reader(self, fd: int) -> bool: ...
    def set_writer(self, fd: int, on_writable: Callable[[], None]) -> None: ...
    def remove_writer(self, fd: int) -> bool: ...

class _FileDescriptorCallbacks:
    __slots__ = ("reader", "writer")
    reader: Callable[[], None]
    writer: Callable[[], None]
    def __init__(self, reader: Callable[[], None] | None = None, writer: Callable[[], None] | None = None) -> None: ...

class _TimerHandle(nbio_interface.AbstractTimerReference):
    def __init__(self, handle: object, loop: AbstractSelectorIOLoop) -> None: ...
    def cancel(self) -> None: ...

class _SelectorIOLoopIOHandle(nbio_interface.AbstractIOReference):
    def __init__(self, subject: _SupportsCancel) -> None: ...
    def cancel(self) -> bool: ...

class _AddressResolver:
    NOT_STARTED: Final = 0
    ACTIVE: Final = 1
    CANCELED: Final = 2
    COMPLETED: Final = 3
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
    def start(self) -> _SelectorIOLoopIOHandle: ...
    def cancel(self) -> bool: ...
