import selectors
import ssl
from _typeshed import Incomplete
from collections.abc import Callable, Iterable, Iterator, Mapping, Sequence
from types import TracebackType
from typing import Final, Literal, TypeAlias, overload
from typing_extensions import Self

from kafka.consumer.fetcher import ConsumerRecord
from kafka.consumer.subscription_state import ConsumerRebalanceListener
from kafka.future import Future
from kafka.serializer.abstract import Deserializer
from kafka.structs import OffsetAndMetadata, OffsetAndTimestamp, TopicPartition

_CommitCallback: TypeAlias = Callable[[Mapping[TopicPartition, OffsetAndMetadata], object], object]
_ConsumerDeserializer: TypeAlias = Deserializer | Callable[[bytes | None], object]

class KafkaConsumer(Iterator[ConsumerRecord]):
    DEFAULT_CONFIG: dict[str, Incomplete]
    DEFAULT_SESSION_TIMEOUT_MS_PRE_KIP_735: Final = 30000
    config: dict[str, Incomplete]
    def __init__(
        self,
        *topics: str,
        bootstrap_servers: str | Sequence[str] = "localhost",
        client_id: str = ...,
        client_rack: str = "",
        group_id: str | None = None,
        group_instance_id: str | None = None,
        key_deserializer: _ConsumerDeserializer | None = None,
        value_deserializer: _ConsumerDeserializer | None = None,
        enable_incremental_fetch_sessions: bool = True,
        fetch_max_wait_ms: int = 500,
        fetch_min_bytes: int = 1,
        fetch_max_bytes: int = 52_428_800,
        max_partition_fetch_bytes: int = 1_048_576,
        request_timeout_ms: int = 30_000,
        retry_backoff_ms: int = 100,
        reconnect_backoff_ms: int = 50,
        reconnect_backoff_max_ms: int = 30000,
        max_in_flight_requests_per_connection: int = 5,
        auto_offset_reset: Literal["earliest", "latest", "smallest", "largest"] = "latest",
        enable_auto_commit: bool = True,
        auto_commit_interval_ms: int = 5000,
        default_offset_commit_callback: _CommitCallback = ...,
        check_crcs: bool = True,
        isolation_level: Literal["read_uncommitted", "read_committed"] = "read_uncommitted",
        allow_auto_create_topics: bool = True,
        metadata_max_age_ms: int = 300000,
        client_dns_lookup: str = "use_all_dns_ips",
        partition_assignment_strategy: Sequence[type[object]] = ...,
        max_poll_records: int = 500,
        max_poll_interval_ms: int = 300000,
        session_timeout_ms: int = 45000,
        heartbeat_interval_ms: int = 3000,
        receive_buffer_bytes: int | None = None,
        send_buffer_bytes: int | None = None,
        receive_message_max_bytes: int = 100_000_000,
        socket_options: Sequence[tuple[int, int, int]] = ...,
        consumer_timeout_ms: int | float = ...,
        security_protocol: Literal["PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"] = "PLAINTEXT",
        ssl_context: ssl.SSLContext | None = None,
        ssl_check_hostname: bool = True,
        ssl_cafile: str | None = None,
        ssl_certfile: str | None = None,
        ssl_keyfile: str | None = None,
        ssl_crlfile: str | None = None,
        ssl_password: str | None = None,
        ssl_ciphers: str | None = None,
        api_version: tuple[int, ...] | None = None,
        bootstrap_timeout_ms: int = 30000,
        connections_max_idle_ms: int = 540000,
        metric_reporters: Sequence[type[object]] = [],
        metrics_enabled: bool = True,
        metrics_num_samples: int = 2,
        metrics_sample_window_ms: int = 30000,
        metric_group_prefix: str = "consumer",
        selector: type[selectors.BaseSelector] = selectors.DefaultSelector,
        exclude_internal_topics: bool = True,
        sasl_mechanism: Literal["PLAIN", "GSSAPI", "OAUTHBEARER", "SCRAM-SHA-256", "SCRAM-SHA-512"] | None = None,
        sasl_plain_username: str | None = None,
        sasl_plain_password: str | None = None,
        sasl_kerberos_name: object | None = None,
        sasl_kerberos_service_name: str = "kafka",
        sasl_kerberos_domain_name: str | None = None,
        sasl_oauth_token_provider: object | None = None,
        proxy_url: str | None = None,
        socks5_proxy: str | None = None,
        kafka_client: Callable[..., object] = ...,
    ) -> None: ...
    def bootstrap_connected(self): ...
    def bootstrap(self, timeout_ms: float | None = None, refresh: bool = False) -> None: ...
    def assign(self, partitions: Iterable[TopicPartition]) -> None: ...
    def assignment(self) -> set[TopicPartition]: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_val: BaseException | None, exc_tb: TracebackType | None
    ) -> None: ...
    def close(self, autocommit: bool = True, timeout_ms: float | None = None) -> None: ...
    def commit_async(
        self, offsets: Mapping[TopicPartition, OffsetAndMetadata] | None = None, callback: _CommitCallback | None = None
    ) -> Future: ...
    def commit(
        self, offsets: Mapping[TopicPartition, OffsetAndMetadata] | None = None, timeout_ms: float | None = None
    ) -> None: ...
    def group_metadata(self): ...

    @overload
    def committed(
        self, partition: TopicPartition, metadata: Literal[False] = False, timeout_ms: float | None = None
    ) -> int | None: ...
    @overload
    def committed(
        self, partition: TopicPartition, metadata: Literal[True], timeout_ms: float | None = None
    ) -> OffsetAndMetadata | None: ...
    @overload
    def committed(
        self, partition: TopicPartition, metadata: bool, timeout_ms: float | None = None
    ) -> int | OffsetAndMetadata | None: ...

    def topics(self) -> set[str]: ...
    def partitions_for_topic(self, topic: str) -> set[int]: ...
    def poll(
        self, timeout_ms: float = 0, max_records: int | None = None, update_offsets: bool = True
    ) -> dict[TopicPartition, list[ConsumerRecord]]: ...
    def position(self, partition: TopicPartition, timeout_ms: float | None = None) -> int | None: ...
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
    def offsets_for_times(
        self, timestamps: Mapping[TopicPartition, int], timeout_ms: float | None = None
    ) -> dict[TopicPartition, OffsetAndTimestamp | None]: ...
    def beginning_offsets(
        self, partitions: Iterable[TopicPartition], timeout_ms: float | None = None
    ) -> dict[TopicPartition, int]: ...
    def end_offsets(self, partitions: Iterable[TopicPartition], timeout_ms: float | None = None) -> dict[TopicPartition, int]: ...
    def __iter__(self) -> Self: ...
    def __next__(self) -> ConsumerRecord: ...
