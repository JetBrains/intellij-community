from _typeshed import Incomplete
from builtins import type as _type
from collections.abc import Mapping
from typing import ClassVar, Final, Literal
from typing_extensions import Self

from pika.amqp_object import Class, Method, Properties
from pika.data import _ArgumentMapping
from pika.delivery_mode import DeliveryMode

PROTOCOL_VERSION: Final[tuple[int, int, int]]
PORT: Final = 5672
ACCESS_REFUSED: Final = 403
CHANNEL_ERROR: Final = 504
COMMAND_INVALID: Final = 503
CONNECTION_FORCED: Final = 320
CONTENT_TOO_LARGE: Final = 311
FRAME_BODY: Final = 3
FRAME_END: Final = 206
FRAME_END_SIZE: Final = 1
FRAME_ERROR: Final = 501
FRAME_HEADER: Final = 2
FRAME_HEADER_SIZE: Final = 7
FRAME_HEARTBEAT: Final = 8
FRAME_MAX_SIZE: Final = 131072
FRAME_METHOD: Final = 1
FRAME_MIN_SIZE: Final = 4096
INTERNAL_ERROR: Final = 541
INVALID_PATH: Final = 402
NOT_ALLOWED: Final = 530
NOT_FOUND: Final = 404
NOT_IMPLEMENTED: Final = 540
NO_CONSUMERS: Final = 313
NO_ROUTE: Final = 312
PERSISTENT_DELIVERY_MODE: Final = 2
PRECONDITION_FAILED: Final = 406
REPLY_SUCCESS: Final = 200
RESOURCE_ERROR: Final = 506
RESOURCE_LOCKED: Final = 405
SYNTAX_ERROR: Final = 502
TRANSIENT_DELIVERY_MODE: Final = 1
UNEXPECTED_FRAME: Final = 505

class Connection(Class):
    INDEX: ClassVar[int]

    class Start(Method):
        INDEX: ClassVar[int]
        version_major: int
        version_minor: int
        server_properties: _ArgumentMapping | None
        mechanisms: str
        locales: str
        def __init__(
            self,
            version_major: int = 0,
            version_minor: int = 9,
            server_properties: _ArgumentMapping | None = None,
            mechanisms: str = "PLAIN",
            locales: str = "en_US",
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class StartOk(Method):
        INDEX: ClassVar[int]
        client_properties: _ArgumentMapping | None
        mechanism: str
        response: str | None
        locale: str
        def __init__(
            self,
            client_properties: _ArgumentMapping | None = None,
            mechanism: str = "PLAIN",
            response: str | None = None,
            locale: str = "en_US",
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Secure(Method):
        INDEX: ClassVar[int]
        challenge: str | None
        def __init__(self, challenge: str | None = None) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class SecureOk(Method):
        INDEX: ClassVar[int]
        response: str
        def __init__(self, response: str | None = None) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Tune(Method):
        INDEX: ClassVar[int]
        channel_max: int
        frame_max: int
        heartbeat: int
        def __init__(self, channel_max: int = 0, frame_max: int = 0, heartbeat: int = 0) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class TuneOk(Method):
        INDEX: ClassVar[int]
        channel_max: int
        frame_max: int
        heartbeat: int
        def __init__(self, channel_max: int = 0, frame_max: int = 0, heartbeat: int = 0) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Open(Method):
        INDEX: ClassVar[int]
        virtual_host: str
        capabilities: str
        insist: bool
        def __init__(self, virtual_host: str = "/", capabilities: str = "", insist: bool = False) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class OpenOk(Method):
        INDEX: ClassVar[int]
        known_hosts: str
        def __init__(self, known_hosts: str = "") -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Close(Method):
        INDEX: ClassVar[int]
        reply_code: int | None
        reply_text: str
        class_id: int | None
        method_id: int | None
        def __init__(
            self, reply_code: int | None = None, reply_text: str = "", class_id: int | None = None, method_id: int | None = None
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class CloseOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Blocked(Method):
        INDEX: ClassVar[int]
        reason: str
        def __init__(self, reason: str = "") -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Unblocked(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class UpdateSecret(Method):
        INDEX: ClassVar[int]
        new_secret: str
        reason: str
        mechanisms: str
        def __init__(self, new_secret: str, reason: str) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class UpdateSecretOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

class Channel(Class):
    INDEX: ClassVar[int]

    class Open(Method):
        INDEX: ClassVar[int]
        out_of_band: str
        def __init__(self, out_of_band: str = "") -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class OpenOk(Method):
        INDEX: ClassVar[int]
        channel_id: str
        def __init__(self, channel_id: str = "") -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Flow(Method):
        INDEX: ClassVar[int]
        active: bool | None
        def __init__(self, active: bool | None = None) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class FlowOk(Method):
        INDEX: ClassVar[int]
        active: bool | None
        def __init__(self, active: bool | None = None) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Close(Method):
        INDEX: ClassVar[int]
        reply_code: int | None
        reply_text: str
        class_id: int | None
        method_id: int | None
        def __init__(
            self, reply_code: int | None = None, reply_text: str = "", class_id: int | None = None, method_id: int | None = None
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class CloseOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

class Access(Class):
    INDEX: ClassVar[int]

    class Request(Method):
        INDEX: ClassVar[int]
        realm: str
        exclusive: bool
        passive: bool
        active: bool
        write: bool
        read: bool
        def __init__(
            self,
            realm: str = "/data",
            exclusive: bool = False,
            passive: bool = True,
            active: bool = True,
            write: bool = True,
            read: bool = True,
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class RequestOk(Method):
        INDEX: ClassVar[int]
        ticket: int
        def __init__(self, ticket: int = 1) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

class Exchange(Class):
    INDEX: ClassVar[int]

    class Declare(Method):
        INDEX: ClassVar[int]
        ticket: int
        exchange: str | None
        type: str
        passive: bool
        durable: bool
        auto_delete: bool
        internal: bool
        nowait: bool
        arguments: Mapping[str, Incomplete] | None
        def __init__(
            self,
            ticket: int = 0,
            exchange: str | None = None,
            type: str = ...,
            passive: bool = False,
            durable: bool = False,
            auto_delete: bool = False,
            internal: bool = False,
            nowait: bool = False,
            arguments: Mapping[str, Incomplete] | None = None,
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class DeclareOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Delete(Method):
        INDEX: ClassVar[int]
        ticket: int
        exchange: str | None
        if_unused: bool
        nowait: bool
        def __init__(
            self, ticket: int = 0, exchange: str | None = None, if_unused: bool = False, nowait: bool = False
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class DeleteOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Bind(Method):
        INDEX: ClassVar[int]
        ticket: int
        destination: str | None
        source: str | None
        routing_key: str
        nowait: bool
        arguments: Mapping[str, Incomplete] | None
        def __init__(
            self,
            ticket: int = 0,
            destination: str | None = None,
            source: str | None = None,
            routing_key: str = "",
            nowait: bool = False,
            arguments: Mapping[str, Incomplete] | None = None,
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class BindOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Unbind(Method):
        INDEX: ClassVar[int]
        ticket: int
        destination: str | None
        source: str | None
        routing_key: str
        nowait: bool
        arguments: Mapping[str, Incomplete] | None
        def __init__(
            self,
            ticket: int = 0,
            destination: str | None = None,
            source: str | None = None,
            routing_key: str = "",
            nowait: bool = False,
            arguments: Mapping[str, Incomplete] | None = None,
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class UnbindOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

class Queue(Class):
    INDEX: ClassVar[int]

    class Declare(Method):
        INDEX: ClassVar[int]
        ticket: int
        queue: str
        passive: bool
        durable: bool
        exclusive: bool
        auto_delete: bool
        nowait: bool
        arguments: Mapping[str, Incomplete] | None
        def __init__(
            self,
            ticket: int = 0,
            queue: str = "",
            passive: bool = False,
            durable: bool = False,
            exclusive: bool = False,
            auto_delete: bool = False,
            nowait: bool = False,
            arguments: Mapping[str, Incomplete] | None = None,
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class DeclareOk(Method):
        INDEX: ClassVar[int]
        queue: str
        message_count: int
        consumer_count: int
        def __init__(self, queue: str, message_count: int, consumer_count: int) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Bind(Method):
        INDEX: ClassVar[int]
        ticket: int
        queue: str
        exchange: str | None
        routing_key: str
        nowait: bool
        arguments: Mapping[str, Incomplete] | None
        def __init__(
            self,
            ticket: int = 0,
            queue: str = "",
            exchange: str | None = None,
            routing_key: str = "",
            nowait: bool = False,
            arguments: Mapping[str, Incomplete] | None = None,
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class BindOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Purge(Method):
        INDEX: ClassVar[int]
        ticket: int
        queue: str
        nowait: bool
        def __init__(self, ticket: int = 0, queue: str = "", nowait: bool = False) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class PurgeOk(Method):
        INDEX: ClassVar[int]
        message_count: int | None
        def __init__(self, message_count: int | None = None) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Delete(Method):
        INDEX: ClassVar[int]
        ticket: int
        queue: str
        if_unused: bool
        if_empty: bool
        nowait: bool
        def __init__(
            self, ticket: int = 0, queue: str = "", if_unused: bool = False, if_empty: bool = False, nowait: bool = False
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class DeleteOk(Method):
        INDEX: ClassVar[int]
        message_count: int | None
        def __init__(self, message_count: int | None = None) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Unbind(Method):
        INDEX: ClassVar[int]
        ticket: int
        queue: str
        exchange: str | None
        routing_key: str
        arguments: Mapping[str, Incomplete] | None
        def __init__(
            self,
            ticket: int = 0,
            queue: str = "",
            exchange: str | None = None,
            routing_key: str = "",
            arguments: Mapping[str, Incomplete] | None = None,
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class UnbindOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

class Basic(Class):
    INDEX: ClassVar[int]

    class Qos(Method):
        INDEX: ClassVar[int]
        prefetch_size: int
        prefetch_count: int
        global_qos: bool
        def __init__(self, prefetch_size: int = 0, prefetch_count: int = 0, global_qos: bool = False) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class QosOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Consume(Method):
        INDEX: ClassVar[int]
        ticket: int
        queue: str
        consumer_tag: str
        no_local: bool
        no_ack: bool
        exclusive: bool
        nowait: bool
        arguments: Mapping[str, Incomplete] | None
        def __init__(
            self,
            ticket: int = 0,
            queue: str = "",
            consumer_tag: str = "",
            no_local: bool = False,
            no_ack: bool = False,
            exclusive: bool = False,
            nowait: bool = False,
            arguments: Mapping[str, Incomplete] | None = None,
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class ConsumeOk(Method):
        INDEX: ClassVar[int]
        consumer_tag: str | None
        def __init__(self, consumer_tag: str | None = None) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Cancel(Method):
        INDEX: ClassVar[int]
        consumer_tag: str | None
        nowait: bool
        def __init__(self, consumer_tag: str | None = None, nowait: bool = False) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class CancelOk(Method):
        INDEX: ClassVar[int]
        consumer_tag: str | None
        def __init__(self, consumer_tag: str | None = None) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Publish(Method):
        INDEX: ClassVar[int]
        ticket: int
        exchange: str
        routing_key: str
        mandatory: bool
        immediate: bool
        def __init__(
            self, ticket: int = 0, exchange: str = "", routing_key: str = "", mandatory: bool = False, immediate: bool = False
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Return(Method):
        INDEX: ClassVar[int]
        reply_code: int | None
        reply_text: str
        exchange: str | None
        routing_key: str | None
        def __init__(
            self, reply_code: int | None = None, reply_text: str = "", exchange: str | None = None, routing_key: str | None = None
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Deliver(Method):
        INDEX: ClassVar[int]
        consumer_tag: str | None
        delivery_tag: int | None
        redelivered: bool
        exchange: str | None
        routing_key: str | None
        def __init__(
            self,
            consumer_tag: str | None = None,
            delivery_tag: int | None = None,
            redelivered: bool = False,
            exchange: str | None = None,
            routing_key: str | None = None,
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Get(Method):
        INDEX: ClassVar[int]
        ticket: int
        queue: str
        no_ack: bool
        def __init__(self, ticket: int = 0, queue: str = "", no_ack: bool = False) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class GetOk(Method):
        INDEX: ClassVar[int]
        delivery_tag: int | None
        redelivered: bool
        exchange: str | None
        routing_key: str | None
        message_count: int | None
        def __init__(
            self,
            delivery_tag: int | None = None,
            redelivered: bool = False,
            exchange: str | None = None,
            routing_key: str | None = None,
            message_count: int | None = None,
        ) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class GetEmpty(Method):
        INDEX: ClassVar[int]
        cluster_id: str
        def __init__(self, cluster_id: str = "") -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Ack(Method):
        INDEX: ClassVar[int]
        delivery_tag: int
        multiple: bool
        def __init__(self, delivery_tag: int = 0, multiple: bool = False) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Reject(Method):
        INDEX: ClassVar[int]
        delivery_tag: int | None
        requeue: bool
        def __init__(self, delivery_tag: int | None = None, requeue: bool = True) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class RecoverAsync(Method):
        INDEX: ClassVar[int]
        requeue: bool
        def __init__(self, requeue: bool = False) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Recover(Method):
        INDEX: ClassVar[int]
        requeue: bool
        def __init__(self, requeue: bool = False) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class RecoverOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Nack(Method):
        INDEX: ClassVar[int]
        delivery_tag: int
        multiple: bool
        requeue: bool
        def __init__(self, delivery_tag: int = 0, multiple: bool = False, requeue: bool = True) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

class Tx(Class):
    INDEX: ClassVar[int]

    class Select(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class SelectOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Commit(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class CommitOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class Rollback(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class RollbackOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

class Confirm(Class):
    INDEX: ClassVar[int]

    class Select(Method):
        INDEX: ClassVar[int]
        nowait: bool
        def __init__(self, nowait: bool = False) -> None: ...
        @property
        def synchronous(self) -> Literal[True]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

    class SelectOk(Method):
        INDEX: ClassVar[int]
        def __init__(self) -> None: ...
        @property
        def synchronous(self) -> Literal[False]: ...
        def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
        def encode(self) -> list[bytes]: ...

class BasicProperties(Properties):
    CLASS: ClassVar[_type[Basic]]
    INDEX: ClassVar[int]
    FLAG_CONTENT_TYPE: Final = 32768
    FLAG_CONTENT_ENCODING: Final = 16384
    FLAG_HEADERS: Final = 8192
    FLAG_DELIVERY_MODE: Final = 4096
    FLAG_PRIORITY: Final = 2048
    FLAG_CORRELATION_ID: Final = 1024
    FLAG_REPLY_TO: Final = 512
    FLAG_EXPIRATION: Final = 256
    FLAG_MESSAGE_ID: Final = 128
    FLAG_TIMESTAMP: Final = 64
    FLAG_TYPE: Final = 32
    FLAG_USER_ID: Final = 16
    FLAG_APP_ID: Final = 8
    FLAG_CLUSTER_ID: Final = 4
    content_type: str | None
    content_encoding: str | None
    headers: _ArgumentMapping | None
    delivery_mode: Literal[1, 2] | None
    priority: int | None
    correlation_id: str | None
    reply_to: str | None
    expiration: str | None
    message_id: str | None
    timestamp: int | None
    type: str | None
    user_id: str | None
    app_id: str | None
    cluster_id: str | None
    def __init__(
        self,
        content_type: str | None = None,
        content_encoding: str | None = None,
        headers: _ArgumentMapping | None = None,
        delivery_mode: DeliveryMode | Literal[1, 2] | None = None,
        priority: int | None = None,
        correlation_id: str | None = None,
        reply_to: str | None = None,
        expiration: str | None = None,
        message_id: str | None = None,
        timestamp: int | None = None,
        type: str | None = None,
        user_id: str | None = None,
        app_id: str | None = None,
        cluster_id: str | None = None,
    ) -> None: ...
    def decode(self, encoded: bytes, offset: int = 0) -> Self: ...
    def encode(self) -> list[bytes]: ...

methods: Final[dict[int, type[Method]]]
props: Final[dict[int, type[BasicProperties]]]

def has_content(methodNumber: int) -> bool: ...
