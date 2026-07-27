import abc
from _typeshed import Incomplete
from collections.abc import Callable, Sequence
from logging import Logger
from typing import Final, Generic, Literal, TypeVar
from typing_extensions import Self

from pika.adapters.utils.connection_workflow import AbstractAMQPConnectionWorkflow, AMQPConnectorException
from pika.adapters.utils.nbio_interface import AbstractIOServices, AbstractStreamProtocol, AbstractStreamTransport
from pika.callback import CallbackManager
from pika.channel import Channel
from pika.connection import Connection, Parameters
from pika.frame import Method
from pika.spec import Connection as SpecConnection

LOGGER: Logger

_IOLoop = TypeVar("_IOLoop")

class BaseConnection(Connection, Generic[_IOLoop], metaclass=abc.ABCMeta):
    def __init__(
        self,
        parameters: Parameters | None,
        on_open_callback: Callable[[Connection], object] | None,
        on_open_error_callback: Callable[[Connection, BaseException], object] | None,
        on_close_callback: Callable[[Connection, BaseException], object] | None,
        nbio: AbstractIOServices,
        internal_connection_workflow: bool = True,
    ) -> None: ...
    @classmethod
    @abc.abstractmethod
    def create_connection(
        cls,
        connection_configs: Sequence[Parameters],
        on_done: Callable[[Connection | AMQPConnectorException], object],
        custom_ioloop: _IOLoop | None = None,
        workflow: AbstractAMQPConnectionWorkflow | None = None,
    ) -> AbstractAMQPConnectionWorkflow: ...
    @property
    def ioloop(self) -> _IOLoop: ...

class _StreamingProtocolShim(AbstractStreamProtocol, Generic[_IOLoop]):
    conn: BaseConnection[_IOLoop]
    def __init__(self, conn: BaseConnection[_IOLoop]) -> None: ...
    # These are defined as None, but on initialization are set as callable attributes
    def connection_made(self, transport: AbstractStreamTransport) -> None: ...
    def connection_lost(self, error: BaseException | None) -> None: ...
    def eof_received(self) -> bool: ...
    def data_received(self, data: bytes) -> None: ...

    # Next attributes are accessed via getattr() from connection.Connection class:
    ON_CONNECTION_CLOSED: Final = "_on_connection_closed"
    ON_CONNECTION_ERROR: Final = "_on_connection_error"
    ON_CONNECTION_OPEN_OK: Final = "_on_connection_open_ok"
    CONNECTION_CLOSED: Final = 0
    CONNECTION_INIT: Final = 1
    CONNECTION_PROTOCOL: Final = 2
    CONNECTION_START: Final = 3
    CONNECTION_TUNE: Final = 4
    CONNECTION_OPEN: Final = 5
    CONNECTION_CLOSING: Final = 6
    connection_state: Literal[0, 1, 2, 3, 4, 5, 6]  # one of the constants above
    params: Parameters
    callbacks: CallbackManager
    server_capabilities: dict[str, bool] | None
    server_properties: dict[str, Incomplete] | None
    known_hosts: str | None
    def add_on_close_callback(self, callback: Callable[[Self, BaseException], object]) -> None: ...
    def add_on_connection_blocked_callback(self, callback: Callable[[Self, Method[SpecConnection.Blocked]], object]) -> None: ...
    def add_on_connection_unblocked_callback(
        self, callback: Callable[[Self, Method[SpecConnection.Unblocked]], object]
    ) -> None: ...
    def add_on_open_callback(self, callback: Callable[[Self], object]) -> None: ...
    def add_on_open_error_callback(
        self, callback: Callable[[Self, BaseException], object], remove_default: bool = True
    ) -> None: ...
    def channel(
        self, channel_number: int | None = None, on_open_callback: Callable[[Channel], object] | None = None
    ) -> Channel: ...
    def update_secret(
        self,
        new_secret: str | bytes,
        reason: str | bytes,
        callback: Callable[[Method[SpecConnection.UpdateSecretOk]], object] | None = None,
    ) -> None: ...
    def close(self, reply_code: int = 200, reply_text: str = "Normal shutdown") -> None: ...
    @property
    def is_closed(self) -> bool: ...
    @property
    def is_closing(self) -> bool: ...
    @property
    def is_open(self) -> bool: ...
    @property
    def basic_nack(self) -> bool: ...
    @property
    def consumer_cancel_notify(self) -> bool: ...
    @property
    def exchange_exchange_bindings(self) -> bool: ...
    @property
    def publisher_confirms(self) -> bool: ...

    # Next attributes are accessed via getattr() from BaseConnection class:
    @classmethod
    def create_connection(
        cls,
        connection_configs: Sequence[Parameters],
        on_done: Callable[[Connection | AMQPConnectorException], object],
        custom_ioloop: _IOLoop | None = None,
        workflow: AbstractAMQPConnectionWorkflow | None = None,
    ) -> AbstractAMQPConnectionWorkflow: ...
    @property
    def ioloop(self) -> _IOLoop: ...
