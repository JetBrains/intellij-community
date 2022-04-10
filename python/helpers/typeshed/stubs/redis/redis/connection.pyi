from _typeshed import Self
from typing import Any, Mapping

from .retry import Retry

ssl_available: Any
SYM_STAR: Any
SYM_DOLLAR: Any
SYM_CRLF: Any
SYM_EMPTY: Any
SERVER_CLOSED_CONNECTION_ERROR: Any

# Options as passed to Pool.get_connection().
_ConnectionPoolOptions = Any

class BaseParser:
    EXCEPTION_CLASSES: Any
    def parse_error(self, response): ...

class SocketBuffer:
    socket_read_size: Any
    bytes_written: Any
    bytes_read: Any
    def __init__(self, socket, socket_read_size, socket_timeout) -> None: ...
    @property
    def length(self): ...
    def read(self, length): ...
    def readline(self): ...
    def purge(self): ...
    def close(self): ...
    def can_read(self, timeout): ...

class PythonParser(BaseParser):
    encoding: Any
    socket_read_size: Any
    def __init__(self, socket_read_size) -> None: ...
    def __del__(self): ...
    def on_connect(self, connection): ...
    def on_disconnect(self): ...
    def can_read(self, timeout): ...
    def read_response(self, disable_decoding: bool = ...): ...

class HiredisParser(BaseParser):
    socket_read_size: Any
    def __init__(self, socket_read_size) -> None: ...
    def __del__(self): ...
    def on_connect(self, connection, **kwargs): ...
    def on_disconnect(self): ...
    def can_read(self, timeout): ...
    def read_from_socket(self, timeout=..., raise_on_timeout: bool = ...) -> bool: ...
    def read_response(self, disable_decoding: bool = ...): ...

DefaultParser: Any

class Encoder:
    def __init__(self, encoding, encoding_errors, decode_responses: bool) -> None: ...
    def encode(self, value: str | bytes | memoryview | bool | float) -> bytes: ...
    def decode(self, value: str | bytes | memoryview, force: bool = ...) -> str: ...

class Connection:
    description_format: Any
    pid: Any
    host: Any
    port: Any
    db: Any
    password: Any
    socket_timeout: Any
    socket_connect_timeout: Any
    socket_keepalive: Any
    socket_keepalive_options: Any
    retry_on_timeout: Any
    retry_on_error: Any
    encoding: Any
    encoding_errors: Any
    decode_responses: Any
    retry: Retry
    redis_connect_func: Any | None
    def __init__(
        self,
        host: str = ...,
        port: int = ...,
        db: int = ...,
        password: str | None = ...,
        socket_timeout: float | None = ...,
        socket_connect_timeout: float | None = ...,
        socket_keepalive: bool = ...,
        socket_keepalive_options: Mapping[str, int | str] | None = ...,
        socket_type: int = ...,
        retry_on_timeout: bool = ...,
        retry_on_error=...,
        encoding: str = ...,
        encoding_errors: str = ...,
        decode_responses: bool = ...,
        parser_class: type[BaseParser] = ...,
        socket_read_size: int = ...,
        health_check_interval: int = ...,
        client_name: str | None = ...,
        username: str | None = ...,
        retry: Retry | None = ...,
        redis_connect_func: Any | None = ...,
    ) -> None: ...
    def __del__(self): ...
    def register_connect_callback(self, callback): ...
    def clear_connect_callbacks(self): ...
    def set_parser(self, parser_class): ...
    def connect(self): ...
    def on_connect(self): ...
    def disconnect(self, *args: object) -> None: ...  # 'args' added in redis 4.1.2
    def check_health(self) -> None: ...
    def send_packed_command(self, command, check_health: bool = ...): ...
    def send_command(self, *args): ...
    def can_read(self, timeout=...): ...
    def read_response(self, disable_decoding: bool = ...): ...
    def pack_command(self, *args): ...
    def pack_commands(self, commands): ...
    def repr_pieces(self) -> list[tuple[str, str]]: ...

class SSLConnection(Connection):
    description_format: Any
    keyfile: Any
    certfile: Any
    cert_reqs: Any
    ca_certs: Any
    ca_path: Any | None
    check_hostname: bool
    certificate_password: Any | None
    ssl_validate_ocsp: bool
    ssl_validate_ocsp_stapled: bool  # added in 4.1.1
    ssl_ocsp_context: Any | None  # added in 4.1.1
    ssl_ocsp_expected_cert: Any | None  # added in 4.1.1
    def __init__(
        self,
        ssl_keyfile=...,
        ssl_certfile=...,
        ssl_cert_reqs=...,
        ssl_ca_certs=...,
        ssl_check_hostname: bool = ...,
        ssl_ca_path: Any | None = ...,
        ssl_password: Any | None = ...,
        ssl_validate_ocsp: bool = ...,
        ssl_validate_ocsp_stapled: bool = ...,  # added in 4.1.1
        ssl_ocsp_context: Any | None = ...,  # added in 4.1.1
        ssl_ocsp_expected_cert: Any | None = ...,  # added in 4.1.1
        **kwargs,
    ) -> None: ...

class UnixDomainSocketConnection(Connection):
    description_format: Any
    pid: Any
    path: Any
    db: Any
    password: Any
    socket_timeout: Any
    retry_on_timeout: Any
    encoding: Any
    encoding_errors: Any
    decode_responses: Any
    retry: Retry
    def __init__(
        self,
        path=...,
        db: int = ...,
        username=...,
        password=...,
        socket_timeout=...,
        encoding: str = ...,
        encoding_errors: str = ...,
        decode_responses: bool = ...,
        retry_on_timeout: bool = ...,
        retry_on_error=...,
        parser_class=...,
        socket_read_size: int = ...,
        health_check_interval: int = ...,
        client_name=...,
        retry: Retry | None = ...,
        redis_connect_func: Any | None = ...,
    ) -> None: ...
    def repr_pieces(self) -> list[tuple[str, str]]: ...

class ConnectionPool:
    @classmethod
    def from_url(cls: type[Self], url: str, *, db: int = ..., decode_components: bool = ..., **kwargs) -> Self: ...
    connection_class: Any
    connection_kwargs: Any
    max_connections: Any
    def __init__(self, connection_class=..., max_connections=..., **connection_kwargs) -> None: ...
    pid: Any
    def reset(self): ...
    def get_connection(self, command_name, *keys, **options: _ConnectionPoolOptions): ...
    def make_connection(self): ...
    def release(self, connection): ...
    def disconnect(self, inuse_connections: bool = ...): ...
    def get_encoder(self) -> Encoder: ...
    def owns_connection(self, connection: Connection) -> bool: ...

class BlockingConnectionPool(ConnectionPool):
    queue_class: Any
    timeout: Any
    def __init__(self, max_connections=..., timeout=..., connection_class=..., queue_class=..., **connection_kwargs) -> None: ...
    pid: Any
    pool: Any
    def reset(self): ...
    def make_connection(self): ...
    def get_connection(self, command_name, *keys, **options: _ConnectionPoolOptions): ...
    def release(self, connection): ...
    def disconnect(self): ...

def to_bool(value: object) -> bool: ...
def parse_url(url: str) -> dict[str, Any]: ...
