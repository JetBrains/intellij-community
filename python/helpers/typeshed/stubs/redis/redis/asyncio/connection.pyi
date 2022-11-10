import asyncio
import enum
import ssl
from collections.abc import Callable, Iterable, Mapping
from typing import Any, Protocol
from typing_extensions import TypeAlias, TypedDict

from redis import RedisError
from redis.asyncio.retry import Retry
from redis.exceptions import ResponseError
from redis.typing import EncodableT, EncodedT

hiredis: Any
NONBLOCKING_EXCEPTION_ERROR_NUMBERS: Any
NONBLOCKING_EXCEPTIONS: Any
SYM_STAR: bytes
SYM_DOLLAR: bytes
SYM_CRLF: bytes
SYM_LF: bytes
SYM_EMPTY: bytes
SERVER_CLOSED_CONNECTION_ERROR: str

class _Sentinel(enum.Enum):
    sentinel: Any

SENTINEL: Any
MODULE_LOAD_ERROR: str
NO_SUCH_MODULE_ERROR: str
MODULE_UNLOAD_NOT_POSSIBLE_ERROR: str
MODULE_EXPORTS_DATA_TYPES_ERROR: str

class _HiredisReaderArgs(TypedDict):
    protocolError: Callable[[str], Exception]
    replyError: Callable[[str], Exception]
    encoding: str | None
    errors: str | None

class Encoder:
    encoding: Any
    encoding_errors: Any
    decode_responses: Any
    def __init__(self, encoding: str, encoding_errors: str, decode_responses: bool) -> None: ...
    def encode(self, value: EncodableT) -> EncodedT: ...
    def decode(self, value: EncodableT, force: bool = ...) -> EncodableT: ...

ExceptionMappingT: TypeAlias = Mapping[str, type[Exception] | Mapping[str, type[Exception]]]

class BaseParser:
    EXCEPTION_CLASSES: ExceptionMappingT
    def __init__(self, socket_read_size: int) -> None: ...
    def __del__(self) -> None: ...
    def parse_error(self, response: str) -> ResponseError: ...
    def on_disconnect(self) -> None: ...
    def on_connect(self, connection: Connection): ...
    async def can_read(self, timeout: float) -> bool: ...
    async def read_response(self, disable_decoding: bool = ...) -> EncodableT | ResponseError | list[EncodableT] | None: ...

class SocketBuffer:
    socket_read_size: Any
    socket_timeout: Any
    bytes_written: int
    bytes_read: int
    def __init__(self, stream_reader: asyncio.StreamReader, socket_read_size: int, socket_timeout: float | None) -> None: ...
    @property
    def length(self): ...
    async def can_read(self, timeout: float) -> bool: ...
    async def read(self, length: int) -> bytes: ...
    async def readline(self) -> bytes: ...
    def purge(self) -> None: ...
    def close(self) -> None: ...

class PythonParser(BaseParser):
    encoder: Any
    def __init__(self, socket_read_size: int) -> None: ...
    def on_connect(self, connection: Connection): ...
    def on_disconnect(self) -> None: ...
    async def can_read(self, timeout: float): ...
    async def read_response(self, disable_decoding: bool = ...) -> EncodableT | ResponseError | None: ...

class HiredisParser(BaseParser):
    def __init__(self, socket_read_size: int) -> None: ...
    def on_connect(self, connection: Connection): ...
    def on_disconnect(self) -> None: ...
    async def can_read(self, timeout: float): ...
    async def read_from_socket(self, timeout: float | None | _Sentinel = ..., raise_on_timeout: bool = ...): ...
    async def read_response(self, disable_decoding: bool = ...) -> EncodableT | list[EncodableT]: ...

DefaultParser: type[PythonParser | HiredisParser]

class ConnectCallbackProtocol(Protocol):
    def __call__(self, connection: Connection): ...

class AsyncConnectCallbackProtocol(Protocol):
    async def __call__(self, connection: Connection): ...

ConnectCallbackT: TypeAlias = ConnectCallbackProtocol | AsyncConnectCallbackProtocol

class Connection:
    pid: Any
    host: Any
    port: Any
    db: Any
    username: Any
    client_name: Any
    password: Any
    socket_timeout: Any
    socket_connect_timeout: Any
    socket_keepalive: Any
    socket_keepalive_options: Any
    socket_type: Any
    retry_on_timeout: Any
    retry_on_error: list[type[RedisError]]
    retry: Retry
    health_check_interval: Any
    next_health_check: int
    ssl_context: Any
    encoder: Any
    redis_connect_func: ConnectCallbackT | None
    def __init__(
        self,
        *,
        host: str = ...,
        port: str | int = ...,
        db: str | int = ...,
        password: str | None = ...,
        socket_timeout: float | None = ...,
        socket_connect_timeout: float | None = ...,
        socket_keepalive: bool = ...,
        socket_keepalive_options: Mapping[int, int | bytes] | None = ...,
        socket_type: int = ...,
        retry_on_timeout: bool = ...,
        retry_on_error: list[type[RedisError]] | _Sentinel = ...,
        encoding: str = ...,
        encoding_errors: str = ...,
        decode_responses: bool = ...,
        parser_class: type[BaseParser] = ...,
        socket_read_size: int = ...,
        health_check_interval: float = ...,
        client_name: str | None = ...,
        username: str | None = ...,
        retry: Retry | None = ...,
        redis_connect_func: ConnectCallbackT | None = ...,
        encoder_class: type[Encoder] = ...,
    ) -> None: ...
    def repr_pieces(self): ...
    def __del__(self) -> None: ...
    @property
    def is_connected(self): ...
    def register_connect_callback(self, callback) -> None: ...
    def clear_connect_callbacks(self) -> None: ...
    def set_parser(self, parser_class) -> None: ...
    async def connect(self) -> None: ...
    async def on_connect(self) -> None: ...
    async def disconnect(self) -> None: ...
    async def check_health(self) -> None: ...
    async def send_packed_command(self, command: bytes | str | Iterable[bytes], check_health: bool = ...): ...
    async def send_command(self, *args, **kwargs) -> None: ...
    async def can_read(self, timeout: float = ...): ...
    async def read_response(self, disable_decoding: bool = ...): ...
    def pack_command(self, *args: EncodableT) -> list[bytes]: ...
    def pack_commands(self, commands: Iterable[Iterable[EncodableT]]) -> list[bytes]: ...

class SSLConnection(Connection):
    ssl_context: Any
    def __init__(
        self,
        ssl_keyfile: str | None = ...,
        ssl_certfile: str | None = ...,
        ssl_cert_reqs: str = ...,
        ssl_ca_certs: str | None = ...,
        ssl_ca_data: str | None = ...,
        ssl_check_hostname: bool = ...,
        **kwargs,
    ) -> None: ...
    @property
    def keyfile(self): ...
    @property
    def certfile(self): ...
    @property
    def cert_reqs(self): ...
    @property
    def ca_certs(self): ...
    @property
    def ca_data(self): ...
    @property
    def check_hostname(self): ...

class RedisSSLContext:
    keyfile: Any
    certfile: Any
    cert_reqs: Any
    ca_certs: Any
    ca_data: Any
    check_hostname: Any
    context: Any
    def __init__(
        self,
        keyfile: str | None = ...,
        certfile: str | None = ...,
        cert_reqs: str | None = ...,
        ca_certs: str | None = ...,
        ca_data: str | None = ...,
        check_hostname: bool = ...,
    ) -> None: ...
    def get(self) -> ssl.SSLContext: ...

class UnixDomainSocketConnection(Connection):
    pid: Any
    path: Any
    db: Any
    username: Any
    client_name: Any
    password: Any
    socket_timeout: Any
    socket_connect_timeout: Any
    retry_on_timeout: Any
    retry_on_error: list[type[RedisError]]
    retry: Any
    health_check_interval: Any
    next_health_check: int
    redis_connect_func: ConnectCallbackT | None
    encoder: Any
    def __init__(
        self,
        *,
        path: str = ...,
        db: str | int = ...,
        username: str | None = ...,
        password: str | None = ...,
        socket_timeout: float | None = ...,
        socket_connect_timeout: float | None = ...,
        encoding: str = ...,
        encoding_errors: str = ...,
        decode_responses: bool = ...,
        retry_on_timeout: bool = ...,
        retry_on_error: list[type[RedisError]] | _Sentinel = ...,
        parser_class: type[BaseParser] = ...,
        socket_read_size: int = ...,
        health_check_interval: float = ...,
        client_name: str | None = ...,
        retry: Retry | None = ...,
        redis_connect_func: ConnectCallbackT | None = ...,
    ) -> None: ...
    def repr_pieces(self) -> Iterable[tuple[str, str | int]]: ...

FALSE_STRINGS: Any

def to_bool(value) -> bool | None: ...

URL_QUERY_ARGUMENT_PARSERS: Mapping[str, Callable[..., object]]

class ConnectKwargs(TypedDict):
    username: str
    password: str
    connection_class: type[Connection]
    host: str
    port: int
    db: int
    path: str

def parse_url(url: str) -> ConnectKwargs: ...

class ConnectionPool:
    @classmethod
    def from_url(cls, url: str, **kwargs) -> ConnectionPool: ...
    connection_class: Any
    connection_kwargs: Any
    max_connections: Any
    encoder_class: Any
    def __init__(
        self, connection_class: type[Connection] = ..., max_connections: int | None = ..., **connection_kwargs
    ) -> None: ...
    pid: Any
    def reset(self) -> None: ...
    async def get_connection(self, command_name, *keys, **options): ...
    def get_encoder(self): ...
    def make_connection(self): ...
    async def release(self, connection: Connection): ...
    def owns_connection(self, connection: Connection): ...
    async def disconnect(self, inuse_connections: bool = ...): ...

class BlockingConnectionPool(ConnectionPool):
    queue_class: Any
    timeout: Any
    def __init__(
        self,
        max_connections: int = ...,
        timeout: int | None = ...,
        connection_class: type[Connection] = ...,
        queue_class: type[asyncio.Queue[Any]] = ...,
        **connection_kwargs,
    ) -> None: ...
    pool: Any
    pid: Any
    def reset(self) -> None: ...
    def make_connection(self): ...
    async def get_connection(self, command_name, *keys, **options): ...
    async def release(self, connection: Connection): ...
    async def disconnect(self, inuse_connections: bool = ...): ...
