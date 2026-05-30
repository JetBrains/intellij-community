import selectors
import ssl
from _typeshed import Incomplete
from collections.abc import Callable, Iterable, Iterator, Mapping, Sequence
from typing import Literal, TypeAlias, TypedDict, overload, type_check_only
from typing_extensions import Self, Unpack

from kafka.consumer.fetcher import ConsumerRecord
from kafka.consumer.subscription_state import ConsumerRebalanceListener
from kafka.future import Future
from kafka.serializer.abstract import Deserializer
from kafka.structs import OffsetAndMetadata, OffsetAndTimestamp, TopicPartition

_ApiVersion: TypeAlias = tuple[int, ...]
_BootstrapServers: TypeAlias = str | Sequence[str]
_CommitCallback: TypeAlias = Callable[[Mapping[TopicPartition, OffsetAndMetadata], object], object]
_ConsumerDeserializer: TypeAlias = Deserializer | Callable[[bytes | None], object]
_KafkaClientFactory: TypeAlias = Callable[..., object]
_SaslMechanism: TypeAlias = Literal["PLAIN", "GSSAPI", "OAUTHBEARER", "SCRAM-SHA-256", "SCRAM-SHA-512"]
_SecurityProtocol: TypeAlias = Literal["PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"]
_SocketOption: TypeAlias = tuple[int, int, int]

@type_check_only
class _KafkaConsumerConfig(TypedDict, total=False):
    bootstrap_servers: _BootstrapServers
    client_id: str
    group_id: str | None
    group_instance_id: str | None
    key_deserializer: _ConsumerDeserializer | None
    value_deserializer: _ConsumerDeserializer | None
    enable_incremental_fetch_sessions: bool
    fetch_max_wait_ms: int
    fetch_min_bytes: int
    fetch_max_bytes: int
    max_partition_fetch_bytes: int
    request_timeout_ms: int
    retry_backoff_ms: int
    reconnect_backoff_ms: int
    reconnect_backoff_max_ms: int
    max_in_flight_requests_per_connection: int
    auto_offset_reset: Literal["earliest", "latest", "smallest", "largest"]
    enable_auto_commit: bool
    auto_commit_interval_ms: int
    default_offset_commit_callback: _CommitCallback
    check_crcs: bool
    isolation_level: Literal["read_uncommitted", "read_committed"]
    allow_auto_create_topics: bool
    metadata_max_age_ms: int
    partition_assignment_strategy: Sequence[type[object]]
    max_poll_records: int
    max_poll_interval_ms: int
    session_timeout_ms: int
    heartbeat_interval_ms: int
    receive_buffer_bytes: int | None
    send_buffer_bytes: int | None
    socket_options: Sequence[_SocketOption]
    sock_chunk_bytes: int
    sock_chunk_buffer_count: int
    consumer_timeout_ms: int | float
    security_protocol: _SecurityProtocol
    ssl_context: ssl.SSLContext | None
    ssl_check_hostname: bool
    ssl_cafile: str | None
    ssl_certfile: str | None
    ssl_keyfile: str | None
    ssl_crlfile: str | None
    ssl_password: str | None
    ssl_ciphers: str | None
    api_version: _ApiVersion | None
    api_version_auto_timeout_ms: int
    connections_max_idle_ms: int
    metric_reporters: Sequence[type[object]]
    metrics_enabled: bool
    metrics_num_samples: int
    metrics_sample_window_ms: int
    metric_group_prefix: str
    selector: type[selectors.BaseSelector]
    exclude_internal_topics: bool
    sasl_mechanism: _SaslMechanism | None
    sasl_plain_username: str | None
    sasl_plain_password: str | None
    sasl_kerberos_name: object | None
    sasl_kerberos_service_name: str
    sasl_kerberos_domain_name: str | None
    sasl_oauth_token_provider: object | None
    socks5_proxy: str | None
    kafka_client: _KafkaClientFactory

log: Incomplete

class KafkaConsumer(Iterator[ConsumerRecord]):
    DEFAULT_CONFIG: Incomplete
    DEFAULT_SESSION_TIMEOUT_MS_0_9: int
    config: Incomplete
    def __init__(self, *topics: str, **configs: Unpack[_KafkaConsumerConfig]) -> None: ...
    def bootstrap_connected(self): ...
    def assign(self, partitions: Iterable[TopicPartition]) -> None: ...
    def assignment(self) -> set[TopicPartition]: ...
    def close(self, autocommit: bool = True, timeout_ms: int | None = None) -> None: ...
    def commit_async(
        self, offsets: Mapping[TopicPartition, OffsetAndMetadata] | None = None, callback: _CommitCallback | None = None
    ) -> Future: ...
    def commit(
        self, offsets: Mapping[TopicPartition, OffsetAndMetadata] | None = None, timeout_ms: int | None = None
    ) -> None: ...

    @overload
    def committed(
        self, partition: TopicPartition, metadata: Literal[False] = False, timeout_ms: int | None = None
    ) -> int | None: ...
    @overload
    def committed(
        self, partition: TopicPartition, metadata: Literal[True], timeout_ms: int | None = None
    ) -> OffsetAndMetadata | None: ...
    @overload
    def committed(
        self, partition: TopicPartition, metadata: bool, timeout_ms: int | None = None
    ) -> int | OffsetAndMetadata | None: ...

    def topics(self) -> set[str]: ...
    def partitions_for_topic(self, topic: str) -> set[int]: ...
    def poll(
        self, timeout_ms: int = 0, max_records: int | None = None, update_offsets: bool = True
    ) -> dict[TopicPartition, list[ConsumerRecord]]: ...
    def position(self, partition: TopicPartition, timeout_ms: int | None = None) -> int | None: ...
    def highwater(self, partition: TopicPartition) -> int | None: ...
    def pause(self, *partitions: TopicPartition) -> None: ...
    def paused(self) -> set[TopicPartition]: ...
    def resume(self, *partitions: TopicPartition) -> None: ...
    def seek(self, partition: TopicPartition, offset: int) -> None: ...
    def seek_to_beginning(self, *partitions: TopicPartition) -> None: ...
    def seek_to_end(self, *partitions: TopicPartition) -> None: ...
    def subscribe(
        self, topics: Iterable[str] = (), pattern: str | None = None, listener: ConsumerRebalanceListener | None = None
    ) -> None: ...
    def subscription(self) -> set[str]: ...
    def unsubscribe(self) -> None: ...
    def metrics(self, raw: bool = False) -> dict[str, dict[str, object]] | dict[object, object] | None: ...
    def offsets_for_times(self, timestamps: Mapping[TopicPartition, int]) -> dict[TopicPartition, OffsetAndTimestamp | None]: ...
    def beginning_offsets(self, partitions: Iterable[TopicPartition]) -> dict[TopicPartition, int]: ...
    def end_offsets(self, partitions: Iterable[TopicPartition]) -> dict[TopicPartition, int]: ...
    def __iter__(self) -> Self: ...
    def __next__(self) -> ConsumerRecord: ...
