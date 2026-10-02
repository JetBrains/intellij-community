import selectors
import ssl
from _typeshed import Incomplete
from collections.abc import Callable, Sequence
from types import TracebackType
from typing import Literal
from typing_extensions import Self

from kafka.admin._acls import ACLAdminMixin
from kafka.admin._cluster import ClusterAdminMixin
from kafka.admin._configs import ConfigAdminMixin
from kafka.admin._groups import GroupAdminMixin
from kafka.admin._partitions import PartitionAdminMixin
from kafka.admin._topics import TopicAdminMixin
from kafka.admin._transactions import TransactionsAdminMixin
from kafka.admin._users import UserAdminMixin
from kafka.net.sasl.oauth import AbstractTokenProvider

class KafkaAdminClient(
    ACLAdminMixin,
    ClusterAdminMixin,
    ConfigAdminMixin,
    GroupAdminMixin,
    PartitionAdminMixin,
    TopicAdminMixin,
    TransactionsAdminMixin,
    UserAdminMixin,
):
    DEFAULT_CONFIG: dict[str, Incomplete]
    config: dict[str, Incomplete]
    def __init__(
        self,
        *,
        bootstrap_servers: str | Sequence[str] = "localhost",
        client_id: str = ...,
        request_timeout_ms: int = 30_000,
        connections_max_idle_ms: int = 540_000,
        reconnect_backoff_ms: int = 50,
        reconnect_backoff_max_ms: int = 30_000,
        max_in_flight_requests_per_connection: int = 5,
        receive_message_max_bytes: int = 100_000_000,
        receive_buffer_bytes: int | None = None,
        send_buffer_bytes: int | None = None,
        socket_options: Sequence[tuple[int, int, int]] = ...,
        retry_backoff_ms: int = 100,
        metadata_max_age_ms: int = 300_000,
        client_dns_lookup: str = "use_all_dns_ips",
        security_protocol: Literal["PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"] = "PLAINTEXT",
        ssl_context: ssl.SSLContext | None = None,
        ssl_check_hostname: bool = True,
        ssl_cafile: str | None = None,
        ssl_certfile: str | None = None,
        ssl_keyfile: str | None = None,
        ssl_password: str | None = None,
        ssl_crlfile: str | None = None,
        api_version: tuple[int, ...] | None = None,
        bootstrap_timeout_ms: int = 30_000,
        selector: type[selectors.BaseSelector] = selectors.DefaultSelector,
        sasl_mechanism: Literal["PLAIN", "GSSAPI", "OAUTHBEARER", "SCRAM-SHA-256", "SCRAM-SHA-512"] | None = None,
        sasl_plain_username: str | None = None,
        sasl_plain_password: str | None = None,
        sasl_kerberos_name: object | None = None,
        sasl_kerberos_service_name: str = "kafka",
        sasl_kerberos_domain_name: str | None = None,
        sasl_oauth_token_provider: AbstractTokenProvider | None = None,
        proxy_url: str | None = None,
        socks5_proxy: str | None = None,
        metric_reporters: Sequence[type[object]] = [],
        metrics_num_samples: int = 2,
        metrics_sample_window_ms: int = 30_000,
        kafka_client: Callable[..., object] = ...,
    ) -> None: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_val: BaseException | None, exc_tb: TracebackType | None
    ) -> None: ...
    def close(self) -> None: ...
