from _typeshed import Incomplete, Self
from collections.abc import Callable, Iterable, Mapping
from queue import Queue
from socket import socket
from typing import Any, ClassVar
from typing_extensions import TypeAlias

from .retry import Retry

ssl_available: bool
SYM_STAR: bytes
SYM_DOLLAR: bytes
SYM_CRLF: bytes
SYM_EMPTY: bytes
SERVER_CLOSED_CONNECTION_ERROR: str
NONBLOCKING_EXCEPTIONS: tuple[type[Exception], ...]
NONBLOCKING_EXCEPTION_ERROR_NUMBERS: dict[type[Exception], int]
SENTINEL: object
MODULE_LOAD_ERROR: str
NO_SUCH_MODULE_ERROR: str
MODULE_UNLOAD_NOT_POSSIBLE_ERROR: str
MODULE_EXPORTS_DATA_TYPES_ERROR: str
FALSE_STRINGS: tuple[str, ...]
URL_QUERY_ARGUMENT_PARSERS: dict[str, Callable[[Any], Any]]

# Options as passed to Pool.get_connection().
_ConnectionPoolOptions: TypeAlias = Any
_ConnectFunc: TypeAlias = Callable[[Connection], object]

class BaseParser:
    EXCEPTION_CLASSES: ClassVar[dict[str, type[Exception] | dict[str, type[Exception]]]]
    def parse_error(self, response: str) -> Exception: ...

class SocketBuffer:
    socket_read_size: int
    bytes_written: int
    bytes_read: int
    socket_timeout: float | None
    def __init__(self, socket: socket, socket_read_size: int, socket_timeout: float | None) -> None: ...
    @property
    def length(self) -> int: ...
    def read(self, length: int) -> bytes: ...
    def readline(self) -> bytes: ...
    def purge(self) -> None: ...
    def close(self) -> None: ...
    def can_read(self, timeout: float | None) -> bool: ...

class PythonParser(BaseParser):
    encoding: str
    socket_read_size: int
    encoder: Encoder | None
    def __init__(self, socket_read_size: int) -> None: ...
    def __del__(self) -> None: ...
    def on_connect(self, connection: Connection) -> None: ...
    def on_disconnect(self) -> None: ...
    def can_read(self, timeout: float | None) -> bool: ...
    def read_response(self, disable_decoding: bool = ...) -> Any: ...  # `str | bytes` or `list[str | bytes]`

class HiredisParser(BaseParser):
    socket_read_size: int
    def __init__(self, socket_read_size: int) -> None: ...
    def __del__(self) -> None: ...
    def on_connect(self, connection: Connection, **kwargs) -> None: ...
    def on_disconnect(self) -> None: ...
    def can_read(self, timeout: float | None) -> bool: ...
    def read_from_socket(self, timeout: float | None = ..., raise_on_timeout: bool = ...) -> bool: ...
    def read_response(self, disable_decoding: bool = ...) -> Any: ...  # `str | bytes` or `list[str | bytes]`

DefaultParser: type[BaseParser]  # Hiredis or PythonParser

class Encoder:
    encoding: str
    encoding_errors: str
    decode_responses: bool
    def __init__(self, encoding: str, encoding_errors: str, decode_responses: bool) -> None: ...
    def encode(self, value: str | bytes | memoryview | bool | float) -> bytes: ...
    def decode(self, value: str | bytes | memoryview, force: bool = ...) -> str: ...

class Connection:
    pid: int
    host: str
    port: int
    db: int
    username: str | None
    password: str | None
    client_name: str | None
    socket_timeout: float | None
    socket_connect_timeout: float | None
    socket_keepalive: bool
    socket_keepalive_options: Mapping[str, int | str]
    socket_type: int
    retry_on_timeout: bool
    retry_on_error: list[type[Exception]]
    retry: Retry
    redis_connect_func: _ConnectFunc | None
    encoder: Encoder
    next_health_check: int
    health_check_interval: int
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
        retry_on_error: list[type[Exception]] = ...,
        encoding: str = ...,
        encoding_errors: str = ...,
        decode_responses: bool = ...,
        parser_class: type[BaseParser] = ...,
        socket_read_size: int = ...,
        health_check_interval: int = ...,
        client_name: str | None = ...,
        username: str | None = ...,
        retry: Retry | None = ...,
        redis_connect_func: _ConnectFunc | None = ...,
    ) -> None: ...
    def __del__(self) -> None: ...
    def register_connect_callback(self, callback: _ConnectFunc) -> None: ...
    def clear_connect_callbacks(self) -> None: ...
    def set_parser(self, parser_class: type[BaseParser]) -> None: ...
    def connect(self) -> None: ...
    def on_connect(self) -> None: ...
    def disconnect(self, *args: object) -> None: ...  # 'args' added in redis 4.1.2
    def check_health(self) -> None: ...
    def send_packed_command(self, command: str | Iterable[str], check_health: bool = ...) -> None: ...
    def send_command(self, *args, **kwargs) -> None: ...
    def can_read(self, timeout: float | None = ...) -> bool: ...
    def read_response(self, disable_decoding: bool = ...) -> Any: ...  # `str | bytes` or `list[str | bytes]`
    def pack_command(self, *args) -> list[bytes]: ...
    def pack_commands(self, commands: Iterable[Iterable[Incomplete]]) -> list[bytes]: ...
    def repr_pieces(self) -> list[tuple[str, str]]: ...

class SSLConnection(Connection):
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
        ssl_ca_data: Any | None = ...,
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
    path: str
    def __init__(
        self,
        path: str = ...,
        db: int = ...,
        username: str | None = ...,
        password: str | None = ...,
        socket_timeout: float | None = ...,
        encoding: str = ...,
        encoding_errors: str = ...,
        decode_responses: bool = ...,
        retry_on_timeout: bool = ...,
        retry_on_error: list[type[Exception]] = ...,
        parser_class: type[BaseParser] = ...,
        socket_read_size: int = ...,
        health_check_interval: int = ...,
        client_name: str | None = ...,
        retry: Retry | None = ...,
        redis_connect_func: _ConnectFunc | None = ...,
    ) -> None: ...

# TODO: make generic on `connection_class`
class ConnectionPool:
    connection_class: type[Connection]
    connection_kwargs: dict[str, Any]
    max_connections: int
    pid: int
    @classmethod
    def from_url(cls: type[Self], url: str, *, db: int = ..., decode_components: bool = ..., **kwargs) -> Self: ...
    def __init__(
        self, connection_class: type[Connection] = ..., max_connections: int | None = ..., **connection_kwargs
    ) -> None: ...
    def reset(self) -> None: ...
    def get_connection(self, command_name: object, *keys, **options: _ConnectionPoolOptions) -> Connection: ...
    def make_connection(self) -> Connection: ...
    def release(self, connection: Connection) -> None: ...
    def disconnect(self, inuse_connections: bool = ...) -> None: ...
    def get_encoder(self) -> Encoder: ...
    def owns_connection(self, connection: Connection) -> bool: ...

class BlockingConnectionPool(ConnectionPool):
    queue_class: type[Queue[Any]]
    timeout: float
    pool: Queue[Connection | None]  # might not be defined
    def __init__(
        self,
        max_connections: int = ...,
        timeout: float = ...,
        connection_class: type[Connection] = ...,
        queue_class: type[Queue[Any]] = ...,
        **connection_kwargs,
    ) -> None: ...
    def disconnect(self) -> None: ...  # type: ignore[override]

def to_bool(value: object) -> bool: ...
def parse_url(url: str) -> dict[str, Any]: ...
