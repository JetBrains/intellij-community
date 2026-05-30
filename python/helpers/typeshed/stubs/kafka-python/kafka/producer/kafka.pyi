import selectors
import ssl
from _typeshed import Incomplete
from collections.abc import Callable, Mapping, Sequence
from typing import Literal, TypeAlias, TypedDict, type_check_only
from typing_extensions import Unpack

from kafka.producer.future import FutureRecordMetadata
from kafka.serializer.abstract import Serializer
from kafka.structs import OffsetAndMetadata, TopicPartition

_ApiVersion: TypeAlias = tuple[int, ...]
_BootstrapServers: TypeAlias = str | Sequence[str]
_KafkaClientFactory: TypeAlias = Callable[..., object]
_Partitioner: TypeAlias = Callable[[bytes | None, Sequence[int], Sequence[int]], int]
_ProducerSerializer: TypeAlias = Serializer | Callable[[object], bytes]
_SaslMechanism: TypeAlias = Literal["PLAIN", "GSSAPI", "OAUTHBEARER", "SCRAM-SHA-256", "SCRAM-SHA-512"]
_SecurityProtocol: TypeAlias = Literal["PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"]
_SocketOption: TypeAlias = tuple[int, int, int]

@type_check_only
class _KafkaProducerConfig(TypedDict, total=False):
    bootstrap_servers: _BootstrapServers
    client_id: str | None
    key_serializer: _ProducerSerializer | None
    value_serializer: _ProducerSerializer | None
    enable_idempotence: bool
    transactional_id: str | None
    transaction_timeout_ms: int
    delivery_timeout_ms: float
    acks: int | Literal["all"]
    bootstrap_topics_filter: set[str]
    compression_type: Literal["gzip", "snappy", "lz4", "zstd"] | None
    retries: int | float
    batch_size: int
    linger_ms: int
    partitioner: _Partitioner
    connections_max_idle_ms: int
    max_block_ms: int
    max_request_size: int
    allow_auto_create_topics: bool
    metadata_max_age_ms: int
    retry_backoff_ms: int
    request_timeout_ms: int
    receive_buffer_bytes: int | None
    send_buffer_bytes: int | None
    socket_options: Sequence[_SocketOption]
    sock_chunk_bytes: int
    sock_chunk_buffer_count: int
    reconnect_backoff_ms: int
    reconnect_backoff_max_ms: int
    max_in_flight_requests_per_connection: int
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
    metric_reporters: Sequence[type[object]]
    metrics_enabled: bool
    metrics_num_samples: int
    metrics_sample_window_ms: int
    selector: type[selectors.BaseSelector]
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
PRODUCER_CLIENT_ID_SEQUENCE: Incomplete

class KafkaProducer:
    DEFAULT_CONFIG: Incomplete
    DEPRECATED_CONFIGS: Incomplete
    config: Incomplete
    def __init__(self, **configs: Unpack[_KafkaProducerConfig]) -> None: ...
    def bootstrap_connected(self): ...
    def __del__(self) -> None: ...
    def close(self, timeout: float | None = None, null_logger: bool = False) -> None: ...
    def partitions_for(self, topic: str) -> set[int]: ...
    @classmethod
    def max_usable_produce_magic(cls, api_version): ...
    def init_transactions(self) -> None: ...
    def begin_transaction(self) -> None: ...
    def send_offsets_to_transaction(
        self, offsets: Mapping[TopicPartition, OffsetAndMetadata], consumer_group_id: str
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
