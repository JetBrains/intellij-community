from _typeshed import Incomplete
from collections import deque
from collections.abc import Callable, Generator, Mapping, Sequence
from logging import Logger
from types import TracebackType
from typing import Final, Generic, TypeVar, overload
from typing_extensions import Self

from pika import connection
from pika.adapters.select_connection import SelectConnection
from pika.channel import Channel
from pika.exchange_type import ExchangeType
from pika.frame import Method
from pika.spec import Basic, BasicProperties, Connection, Exchange, Queue, Tx

T = TypeVar("T", bound=Connection.Blocked | Connection.Unblocked)  # noqa: Y001

LOGGER: Logger

class _IoloopTimerContext:
    def __init__(self, duration: float, connection: SelectConnection) -> None: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, value: BaseException | None, traceback: TracebackType | None
    ) -> None: ...
    def is_ready(self) -> bool: ...

class _TimerEvt:
    __slots__ = ("timer_id", "_callback")
    timer_id: object | None
    def __init__(self, callback: Callable[[], None]) -> None: ...
    def dispatch(self) -> None: ...

class _ConnectionBlockedUnblockedEvtBase(Generic[T]):
    __slots__ = ("_callback", "_method_frame")
    def __init__(self, callback: Callable[[Method[T]], None], method_frame: Method[T]) -> None: ...
    def dispatch(self) -> None: ...

class _ConnectionBlockedEvt(_ConnectionBlockedUnblockedEvtBase[Connection.Blocked]): ...
class _ConnectionUnblockedEvt(_ConnectionBlockedUnblockedEvtBase[Connection.Unblocked]): ...

class BlockingConnection:
    def __init__(
        self,
        parameters: connection.Parameters | Sequence[connection.Parameters] | None = None,
        _impl_class: SelectConnection | None = None,
    ) -> None: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, value: BaseException | None, traceback: TracebackType | None
    ) -> None: ...
    def add_on_connection_blocked_callback(
        self, callback: Callable[[connection.Connection, Method[Connection.Blocked]], None]
    ) -> None: ...
    def add_on_connection_unblocked_callback(
        self, callback: Callable[[connection.Connection, Method[Connection.Unblocked]], None]
    ) -> None: ...
    def call_later(self, delay: float, callback: Callable[[], None]) -> object: ...
    def add_callback_threadsafe(self, callback: Callable[..., None]) -> None: ...
    def remove_timeout(self, timeout_id: object) -> None: ...
    def update_secret(self, new_secret: str, reason: str) -> None: ...
    def close(self, reply_code: int = 200, reply_text: str = "Normal shutdown") -> None: ...
    def process_data_events(self, time_limit: float | None = 0) -> None: ...
    def sleep(self, duration: float) -> None: ...
    def channel(self, channel_number: int | None = None) -> BlockingChannel: ...
    @property
    def is_closed(self) -> bool: ...
    @property
    def is_open(self) -> bool: ...
    @property
    def basic_nack_supported(self) -> bool: ...
    @property
    def consumer_cancel_notify_supported(self) -> bool: ...
    @property
    def exchange_exchange_bindings_supported(self) -> bool: ...
    @property
    def publisher_confirms_supported(self) -> bool: ...
    basic_nack = basic_nack_supported
    consumer_cancel_notify = consumer_cancel_notify_supported
    exchange_exchange_bindings = exchange_exchange_bindings_supported
    publisher_confirms = publisher_confirms_supported

class _ChannelPendingEvt: ...

class _ConsumerDeliveryEvt(_ChannelPendingEvt):
    __slots__ = ("method", "properties", "body")
    method: Basic.Deliver
    properties: BasicProperties
    body: bytes
    def __init__(self, method: Basic.Deliver, properties: BasicProperties, body: bytes) -> None: ...

class _ConsumerCancellationEvt(_ChannelPendingEvt):
    __slots__ = ("method_frame",)
    method_frame: Method[Basic.Cancel]
    def __init__(self, method_frame: Method[Basic.Cancel]) -> None: ...
    @property
    def method(self) -> Basic.Cancel: ...

class _ReturnedMessageEvt(_ChannelPendingEvt):
    __slots__ = ("callback", "channel", "method", "properties", "body")
    callback: Callable[[BlockingChannel, Basic.Return, BasicProperties, bytes], None]
    channel: BlockingChannel
    method: Basic.Return
    properties: BasicProperties
    body: bytes
    def __init__(
        self,
        callback: Callable[[BlockingChannel, Basic.Return, BasicProperties, bytes], None],
        channel: BlockingChannel,
        method: Basic.Return,
        properties: BasicProperties,
        body: bytes,
    ) -> None: ...
    def dispatch(self) -> None: ...

class ReturnedMessage:
    __slots__ = ("method", "properties", "body")
    method: Basic.Return
    properties: BasicProperties
    body: bytes
    def __init__(self, method: Basic.Return, properties: BasicProperties, body: bytes) -> None: ...

class _ConsumerInfo:
    __slots__ = ("consumer_tag", "auto_ack", "on_message_callback", "alternate_event_sink", "state")
    SETTING_UP: Final = 1
    ACTIVE: Final = 2
    TEARING_DOWN: Final = 3
    CANCELLED_BY_BROKER: Final = 4
    consumer_tag: str
    auto_ack: bool
    on_message_callback: Callable[[BlockingChannel, Basic.Deliver, BasicProperties, bytes], None] | None
    alternate_event_sink: Callable[[_ChannelPendingEvt], None] | None
    state: int

    @overload
    def __init__(
        self,
        consumer_tag: str,
        auto_ack: bool,
        # Only one of them must be non-None:
        on_message_callback: Callable[[BlockingChannel, Basic.Deliver, BasicProperties, bytes], None],
        alternate_event_sink: None = None,
    ) -> None: ...
    @overload
    def __init__(
        self,
        consumer_tag: str,
        auto_ack: bool,
        # Only one of them must be non-None:
        on_message_callback: None = None,
        alternate_event_sink: Callable[[_ChannelPendingEvt], None] = ...,
    ) -> None: ...

    @property
    def setting_up(self) -> bool: ...
    @property
    def active(self) -> bool: ...
    @property
    def tearing_down(self) -> bool: ...
    @property
    def cancelled_by_broker(self) -> bool: ...

class _QueueConsumerGeneratorInfo:
    __slots__ = ("params", "consumer_tag", "pending_events")
    params: tuple[str, bool, bool]
    consumer_tag: str
    pending_events: deque[_ChannelPendingEvt]
    def __init__(self, params: tuple[str, bool, bool], consumer_tag: str) -> None: ...

class BlockingChannel:
    def __init__(self, channel_impl: Channel, connection: BlockingConnection) -> None: ...
    def __int__(self) -> int: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, value: BaseException | None, traceback: TracebackType | None
    ) -> None: ...
    @property
    def channel_number(self) -> int: ...
    @property
    def connection(self) -> BlockingConnection: ...
    @property
    def is_closed(self) -> bool: ...
    @property
    def is_open(self) -> bool: ...
    @property
    def consumer_tags(self) -> list[str]: ...
    def close(self, reply_code: int = 0, reply_text: str = "Normal shutdown") -> None: ...
    def flow(self, active: bool) -> bool: ...
    def add_on_cancel_callback(self, callback: Callable[[Method[Basic.Cancel]], None]) -> None: ...
    def add_on_return_callback(
        self, callback: Callable[[BlockingChannel, Basic.Return, BasicProperties, bytes], None]
    ) -> None: ...
    def basic_consume(
        self,
        queue: str,
        on_message_callback: Callable[[BlockingChannel, Basic.Deliver, BasicProperties, bytes], None],
        auto_ack: bool = False,
        exclusive: bool = False,
        consumer_tag: str | None = None,
        arguments: Mapping[str, Incomplete] | None = None,
    ) -> str: ...
    def basic_cancel(self, consumer_tag: str) -> list[tuple[Basic.Deliver, BasicProperties, bytes]]: ...
    def start_consuming(self) -> None: ...
    def stop_consuming(self, consumer_tag: str | None = None) -> None: ...

    @overload
    def consume(
        self,
        queue: str,
        auto_ack: bool = False,
        exclusive: bool = False,
        arguments: Mapping[str, Incomplete] | None = None,
        inactivity_timeout: None = None,  # different between overloads
        consumer_tag: str | None = None,
    ) -> Generator[tuple[Basic.Deliver, BasicProperties, bytes]]: ...
    @overload
    def consume(
        self,
        queue: str,
        auto_ack: bool = False,
        exclusive: bool = False,
        arguments: Mapping[str, Incomplete] | None = None,
        inactivity_timeout: float = ...,  # different between overloads
        consumer_tag: str | None = None,
    ) -> Generator[tuple[Basic.Deliver, BasicProperties, bytes] | tuple[None, None, None]]: ...

    def get_waiting_message_count(self) -> int: ...
    def cancel(self) -> int: ...
    def basic_ack(self, delivery_tag: int = 0, multiple: bool = False) -> None: ...
    def basic_nack(self, delivery_tag: int = 0, multiple: bool = False, requeue: bool = True) -> None: ...
    def basic_get(
        self, queue: str, auto_ack: bool = False
    ) -> tuple[Basic.GetOk, BasicProperties, bytes] | tuple[None, None, None]: ...
    def basic_publish(
        self,
        exchange: str,
        routing_key: str,
        body: str | bytes,
        properties: BasicProperties | None = None,
        mandatory: bool = False,
    ) -> None: ...
    def basic_qos(self, prefetch_size: int = 0, prefetch_count: int = 0, global_qos: bool = False) -> None: ...
    def basic_recover(self, requeue: bool = False) -> None: ...
    def basic_reject(self, delivery_tag: int = 0, requeue: bool = True) -> None: ...
    def confirm_delivery(self) -> None: ...
    def exchange_declare(
        self,
        exchange: str,
        exchange_type: ExchangeType | str = ...,
        passive: bool = False,
        durable: bool = False,
        auto_delete: bool = False,
        internal: bool = False,
        arguments: Mapping[str, Incomplete] | None = None,
    ) -> None: ...
    def exchange_delete(self, exchange: str | None = None, if_unused: bool = False) -> Method[Exchange.DeleteOk]: ...
    def exchange_bind(
        self, destination: str, source: str, routing_key: str = "", arguments: Mapping[str, Incomplete] | None = None
    ) -> Method[Exchange.BindOk]: ...
    def exchange_unbind(
        self,
        destination: str | None = None,
        source: str | None = None,
        routing_key: str = "",
        arguments: Mapping[str, Incomplete] | None = None,
    ) -> Method[Exchange.UnbindOk]: ...
    def queue_declare(
        self,
        queue: str,
        passive: bool = False,
        durable: bool = False,
        exclusive: bool = False,
        auto_delete: bool = False,
        arguments: Mapping[str, Incomplete] | None = None,
    ) -> Method[Queue.DeclareOk]: ...
    def queue_delete(self, queue: str, if_unused: bool = False, if_empty: bool = False) -> Method[Queue.DeleteOk]: ...
    def queue_purge(self, queue: str) -> Method[Queue.PurgeOk]: ...
    def queue_bind(
        self, queue: str, exchange: str, routing_key: str | None = None, arguments: Mapping[str, Incomplete] | None = None
    ) -> Method[Queue.BindOk]: ...
    def queue_unbind(
        self, queue: str, exchange: str, routing_key: str | None = None, arguments: Mapping[str, Incomplete] | None = None
    ) -> Method[Queue.UnbindOk]: ...
    def tx_select(self) -> Method[Tx.SelectOk]: ...
    def tx_commit(self) -> Method[Tx.CommitOk]: ...
    def tx_rollback(self) -> Method[Tx.RollbackOk]: ...
