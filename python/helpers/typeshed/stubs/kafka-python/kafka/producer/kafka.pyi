import selectors
import ssl
from _typeshed import Incomplete
from collections.abc import Callable, Mapping, Sequence
from types import TracebackType
from typing import Literal, TypeAlias
from typing_extensions import Self

from kafka.producer.future import FutureRecordMetadata
from kafka.producer.record_accumulator import AtomicInteger
from kafka.serializer.abstract import Serializer
from kafka.structs import ConsumerGroupMetadata, OffsetAndMetadata, TopicPartition

_Partitioner: TypeAlias = Callable[[bytes | None, Sequence[int], Sequence[int]], int]
_ProducerSerializer: TypeAlias = Serializer | Callable[[object], bytes]

PRODUCER_CLIENT_ID_SEQUENCE: AtomicInteger

class KafkaProducer:
    DEFAULT_CONFIG: dict[str, Incomplete]
    DEPRECATED_CONFIGS: tuple[str, ...]
    config: dict[str, Incomplete]
    def __init__(
        self,
        *,
        bootstrap_servers: str | Sequence[str] = "localhost",
        client_id: str | None = None,
        key_serializer: _ProducerSerializer | None = None,
        value_serializer: _ProducerSerializer | None = None,
        enable_idempotence: bool = True,
        transactional_id: str | None = None,
        transaction_timeout_ms: int = 60000,
        delivery_timeout_ms: float = 120000,
        acks: int | Literal["all"] = -1,
        compression_type: Literal["gzip", "snappy", "lz4", "zstd"] | None = None,
        retries: int | float = ...,
        batch_size: int = 16384,
        linger_ms: int = 0,
        partitioner: _Partitioner = ...,
        connections_max_idle_ms: int = 540000,
        max_block_ms: int = 60000,
        max_request_size: int = 1048576,
        allow_auto_create_topics: bool = True,
        metadata_max_age_ms: int = 300000,
        client_dns_lookup: str = "use_all_dns_ips",
        retry_backoff_ms: int = 100,
        request_timeout_ms: int = 30000,
        receive_message_max_bytes: int = 100_000_000,
        receive_buffer_bytes: int | None = None,
        send_buffer_bytes: int | None = None,
        socket_options: Sequence[tuple[int, int, int]] = ...,
        reconnect_backoff_ms: int = 50,
        reconnect_backoff_max_ms: int = 30000,
        max_in_flight_requests_per_connection: int = 5,
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
        metric_reporters: Sequence[type[object]] = [],
        metrics_enabled: bool = True,
        metrics_num_samples: int = 2,
        metrics_sample_window_ms: int = 30000,
        selector: type[selectors.BaseSelector] = selectors.DefaultSelector,
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
    def bootstrap_connected(self) -> bool: ...
    def __del__(self) -> None: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_val: BaseException | None, exc_tb: TracebackType | None
    ) -> None: ...
    def close(self, timeout: float | None = None, null_logger: bool = False) -> None: ...
    def partitions_for(self, topic: str) -> set[int]: ...
    @classmethod
    def max_usable_produce_magic(cls, api_version): ...
    def init_transactions(self) -> None: ...
    def begin_transaction(self) -> None: ...
    def send_offsets_to_transaction(
        self, offsets: Mapping[TopicPartition, OffsetAndMetadata], group_metadata: str | ConsumerGroupMetadata
    ) -> None: ...
    def commit_transaction(self) -> None: ...
    def abort_transaction(self) -> None: ...
    def send(
        self,
        topic: str,
        value: object = None,
        key: object = None,
        headers: Sequence[tuple[str, bytes]] | None = None,
        partition: int | None = None,
        timestamp_ms: int | None = None,
    ) -> FutureRecordMetadata: ...
    def flush(self, timeout: float | None = None) -> None: ...
    def metrics(self, raw: bool = False) -> dict[str, dict[str, object]] | dict[object, object] | None: ...
