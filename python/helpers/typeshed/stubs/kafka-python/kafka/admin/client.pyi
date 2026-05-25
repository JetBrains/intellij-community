import selectors
import ssl
from _typeshed import Incomplete
from collections.abc import Callable, Iterable, Mapping, Sequence
from typing import Literal, TypeAlias, TypedDict, type_check_only
from typing_extensions import Unpack

from kafka.admin.acl_resource import ACL, ACLFilter
from kafka.admin.config_resource import ConfigResource
from kafka.admin.new_partitions import NewPartitions
from kafka.admin.new_topic import NewTopic
from kafka.errors import KafkaError
from kafka.protocol.admin import ElectionType
from kafka.structs import GroupInformation, OffsetAndMetadata, TopicPartition

_ApiVersion: TypeAlias = tuple[int, ...]
_BootstrapServers: TypeAlias = str | Sequence[str]
_KafkaClientFactory: TypeAlias = Callable[..., object]
_SaslMechanism: TypeAlias = Literal["PLAIN", "GSSAPI", "OAUTHBEARER", "SCRAM-SHA-256", "SCRAM-SHA-512"]
_SecurityProtocol: TypeAlias = Literal["PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"]
_SocketOption: TypeAlias = tuple[int, int, int]

@type_check_only
class _KafkaAdminClientConfig(TypedDict, total=False):
    bootstrap_servers: _BootstrapServers
    client_id: str
    request_timeout_ms: int
    connections_max_idle_ms: int
    reconnect_backoff_ms: int
    reconnect_backoff_max_ms: int
    max_in_flight_requests_per_connection: int
    receive_buffer_bytes: int | None
    send_buffer_bytes: int | None
    socket_options: Sequence[_SocketOption]
    sock_chunk_bytes: int
    sock_chunk_buffer_count: int
    retry_backoff_ms: int
    metadata_max_age_ms: int
    security_protocol: _SecurityProtocol
    ssl_context: ssl.SSLContext | None
    ssl_check_hostname: bool
    ssl_cafile: str | None
    ssl_certfile: str | None
    ssl_keyfile: str | None
    ssl_password: str | None
    ssl_crlfile: str | None
    api_version: _ApiVersion | None
    api_version_auto_timeout_ms: int
    selector: type[selectors.BaseSelector]
    sasl_mechanism: _SaslMechanism | None
    sasl_plain_username: str | None
    sasl_plain_password: str | None
    sasl_kerberos_name: object | None
    sasl_kerberos_service_name: str
    sasl_kerberos_domain_name: str | None
    sasl_oauth_token_provider: object | None
    socks5_proxy: str | None
    metric_reporters: Sequence[type[object]]
    metrics_num_samples: int
    metrics_sample_window_ms: int
    kafka_client: _KafkaClientFactory

@type_check_only
class _CreateAclsResult(TypedDict):
    succeeded: list[ACL]
    failed: list[tuple[ACL, KafkaError]]

log: Incomplete

class KafkaAdminClient:
    DEFAULT_CONFIG: Incomplete
    config: Incomplete
    def __init__(self, **configs: Unpack[_KafkaAdminClientConfig]) -> None: ...
    def close(self) -> None: ...
    def send_request(self, request, node_id=None): ...
    def send_requests(self, requests_and_node_ids, response_fn=...): ...
    def create_topics(self, new_topics: Sequence[NewTopic], timeout_ms: int | None = None, validate_only: bool = False): ...
    def delete_topics(self, topics: Sequence[str], timeout_ms: int | None = None): ...
    def list_topics(self) -> list[str]: ...
    def describe_topics(self, topics: Sequence[str] | None = None) -> list[dict[str, Incomplete]]: ...
    def describe_cluster(self) -> dict[str, Incomplete]: ...
    def describe_acls(self, acl_filter: ACLFilter) -> tuple[list[ACL], KafkaError]: ...
    def create_acls(self, acls: Sequence[ACL]) -> _CreateAclsResult: ...
    def delete_acls(
        self, acl_filters: Sequence[ACLFilter]
    ) -> list[tuple[ACLFilter, list[tuple[ACL, KafkaError]], KafkaError]]: ...
    def describe_configs(self, config_resources: Sequence[ConfigResource], include_synonyms: bool = False): ...
    def alter_configs(self, config_resources: Sequence[ConfigResource]): ...
    def create_partitions(
        self, topic_partitions: Mapping[str, NewPartitions], timeout_ms: int | None = None, validate_only: bool = False
    ): ...
    def delete_records(
        self,
        records_to_delete: Mapping[TopicPartition, int],
        timeout_ms: float | None = None,
        partition_leader_id: int | None = None,
    ) -> dict[TopicPartition, Incomplete]: ...
    def describe_consumer_groups(
        self, group_ids: Sequence[str], group_coordinator_id: int | None = None, include_authorized_operations: bool = False
    ) -> list[GroupInformation]: ...
    def list_consumer_groups(self, broker_ids: Sequence[int] | None = None) -> list[tuple[str, str]]: ...
    def list_consumer_group_offsets(
        self, group_id: str, group_coordinator_id: int | None = None, partitions: Iterable[TopicPartition] | None = None
    ) -> dict[TopicPartition, OffsetAndMetadata]: ...
    def delete_consumer_groups(
        self, group_ids: Sequence[str], group_coordinator_id: int | None = None
    ) -> list[tuple[str, KafkaError]]: ...
    def perform_leader_election(
        self,
        election_type: int | ElectionType,
        topic_partitions: Mapping[str, Sequence[int]] | None = None,
        timeout_ms: int | None = None,
    ): ...
    def describe_log_dirs(self): ...
