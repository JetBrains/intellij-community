import asyncio
import enum
import ssl
from _typeshed import Unused
from abc import abstractmethod
from collections.abc import Callable, Iterable, Mapping
from types import MappingProxyType
from typing import Any, Final, Generic, Literal, Protocol, TypedDict, TypeVar, overload
from typing_extensions import Self, TypeAlias

from redis.asyncio.retry import Retry
from redis.credentials import CredentialProvider
from redis.exceptions import AuthenticationError, RedisError, ResponseError
from redis.typing import EncodableT, EncodedT

_SSLVerifyMode: TypeAlias = Literal["none", "optional", "required"]

SYM_STAR: Final[bytes]
SYM_DOLLAR: Final[bytes]
SYM_CRLF: Final[bytes]
SYM_LF: Final[bytes]
SYM_EMPTY: Final[bytes]

SERVER_CLOSED_CONNECTION_ERROR: Final[str]

class _Sentinel(enum.Enum):
    sentinel = object()

SENTINEL: Final[object]
MODULE_LOAD_ERROR: Final[str]
NO_SUCH_MODULE_ERROR: Final[str]
MODULE_UNLOAD_NOT_POSSIBLE_ERROR: Final[str]
MODULE_EXPORTS_DATA_TYPES_ERROR: Final[str]
NO_AUTH_SET_ERROR: Final[dict[str, type[AuthenticationError]]]

class Encoder:
    encoding: str
    encoding_errors: str
    decode_responses: bool
    def __init__(self, encoding: str, encoding_errors: str, decode_responses: bool) -> None: ...
    def encode(self, value: EncodableT) -> EncodedT: ...
    def decode(self, value: EncodableT, force: bool = False) -> EncodableT: ...

ExceptionMappingT: TypeAlias = Mapping[str, type[Exception] | Mapping[str, type[Exception]]]

class BaseParser:
    EXCEPTION_CLASSES: ExceptionMappingT
    def __init__(self, socket_read_size: int) -> None: ...
    @classmethod
    def parse_error(cls, response: str) -> ResponseError: ...
    @abstractmethod
    def on_disconnect(self) -> None: ...
    @abstractmethod
    def on_connect(self, connection: AbstractConnection) -> None: ...
    @abstractmethod
    async def can_read_destructive(self) -> bool: ...
    @abstractmethod
    async def read_response(self, disable_decoding: bool = False) -> EncodableT | ResponseError | list[EncodableT] | None: ...

class PythonParser(BaseParser):
    encoder: Encoder | None
    def __init__(self, socket_read_size: int) -> None: ...
    def on_connect(self, connection: AbstractConnection) -> None: ...
    def on_disconnect(self) -> None: ...
    async def can_read_destructive(self) -> bool: ...
    async def read_response(self, disable_decoding: bool = False) -> EncodableT | ResponseError | None: ...

class HiredisParser(BaseParser):
    def __init__(self, socket_read_size: int) -> None: ...
    def on_connect(self, connection: AbstractConnection) -> None: ...
    def on_disconnect(self) -> None: ...
    async def can_read_destructive(self) -> bool: ...
    async def read_from_socket(self) -> Literal[True]: ...
    async def read_response(self, disable_decoding: bool = False) -> EncodableT | list[EncodableT]: ...

DefaultParser: type[PythonParser | HiredisParser]

class ConnectCallbackProtocol(Protocol):
    def __call__(self, connection: Connection): ...

class AsyncConnectCallbackProtocol(Protocol):
    async def __call__(self, connection: Connection): ...

ConnectCallbackT: TypeAlias = ConnectCallbackProtocol | AsyncConnectCallbackProtocol

class AbstractConnection:
    pid: int
    db: str | int
    client_name: str | None
    credential_provider: CredentialProvider | None
    password: str | None
    username: str | None
    socket_timeout: float | None
    socket_connect_timeout: float | None
    retry_on_timeout: bool
    retry_on_error: list[type[Exception]]
    retry: Retry
    health_check_interval: float
    next_health_check: float
    encoder: Encoder
    redis_connect_func: ConnectCallbackT | None

    def __init__(
        self,
        *,
        db: str | int = 0,
        password: str | None = None,
        socket_timeout: float | None = None,
        socket_connect_timeout: float | None = None,
        retry_on_timeout: bool = False,
        retry_on_error: list[type[RedisError]] | _Sentinel = ...,
        encoding: str = "utf-8",
        encoding_errors: str = "strict",
        decode_responses: bool = False,
        parser_class: type[BaseParser] = ...,
        socket_read_size: int = 65536,
        health_check_interval: float = 0,
        client_name: str | None = None,
        username: str | None = None,
        retry: Retry | None = None,
        redis_connect_func: ConnectCallbackT | None = None,
        encoder_class: type[Encoder] = ...,
        credential_provider: CredentialProvider | None = None,
    ) -> None: ...
    @abstractmethod
    def repr_pieces(self) -> list[tuple[str, Any]]: ...
    @property
    def is_connected(self) -> bool: ...
    def register_connect_callback(self, callback: ConnectCallbackT) -> None: ...
    def clear_connect_callbacks(self) -> None: ...
    def set_parser(self, parser_class: type[BaseParser]) -> None: ...
    async def connect(self) -> None: ...
    async def on_connect(self) -> None: ...
    async def disconnect(self, nowait: bool = False) -> None: ...
    async def check_health(self) -> None: ...
    async def send_packed_command(self, command: bytes | str | Iterable[bytes], check_health: bool = True) -> None: ...
    async def send_command(self, *args: Any, **kwargs: Any) -> None: ...
    async def can_read_destructive(self) -> bool: ...
    async def read_response(
        self, disable_decoding: bool = False, timeout: float | None = None, *, disconnect_on_error: bool = True
    ) -> EncodableT | list[EncodableT] | None: ...
    def pack_command(self, *args: EncodableT) -> list[bytes]: ...
    def pack_commands(self, commands: Iterable[Iterable[EncodableT]]) -> list[bytes]: ...

class Connection(AbstractConnection):
    host: str
    port: int
    socket_keepalive: bool
    socket_keepalive_options: Mapping[int, int | bytes] | None
    socket_type: int

    def __init__(
        self,
        *,
        host: str = "localhost",
        port: str | int = 6379,
        socket_keepalive: bool = False,
        socket_keepalive_options: Mapping[int, int | bytes] | None = None,
        socket_type: int = 0,
        # **kwargs forwarded to AbstractConnection.
        db: str | int = 0,
        password: str | None = None,
        socket_timeout: float | None = None,
        socket_connect_timeout: float | None = None,
        retry_on_timeout: bool = False,
        retry_on_error: list[type[RedisError]] | _Sentinel = ...,
        encoding: str = "utf-8",
        encoding_errors: str = "strict",
        decode_responses: bool = False,
        parser_class: type[BaseParser] = ...,
        socket_read_size: int = 65536,
        health_check_interval: float = 0,
        client_name: str | None = None,
        username: str | None = None,
        retry: Retry | None = None,
        redis_connect_func: ConnectCallbackT | None = None,
        encoder_class: type[Encoder] = ...,
        credential_provider: CredentialProvider | None = None,
    ) -> None: ...
    def repr_pieces(self) -> list[tuple[str, Any]]: ...

class SSLConnection(Connection):
    ssl_context: RedisSSLContext
    def __init__(
        self,
        ssl_keyfile: str | None = None,
        ssl_certfile: str | None = None,
        ssl_cert_reqs: _SSLVerifyMode = "required",
        ssl_ca_certs: str | None = None,
        ssl_ca_data: str | None = None,
        ssl_check_hostname: bool = False,
        *,
        # **kwargs forwarded to Connection.
        host: str = "localhost",
        port: str | int = 6379,
        socket_keepalive: bool = False,
        socket_keepalive_options: Mapping[int, int | bytes] | None = None,
        socket_type: int = 0,
        db: str | int = 0,
        password: str | None = None,
        socket_timeout: float | None = None,
        socket_connect_timeout: float | None = None,
        retry_on_timeout: bool = False,
        retry_on_error: list[type[RedisError]] | _Sentinel = ...,
        encoding: str = "utf-8",
        encoding_errors: str = "strict",
        decode_responses: bool = False,
        parser_class: type[BaseParser] = ...,
        socket_read_size: int = 65536,
        health_check_interval: float = 0,
        client_name: str | None = None,
        username: str | None = None,
        retry: Retry | None = None,
        redis_connect_func: ConnectCallbackT | None = None,
        encoder_class: type[Encoder] = ...,
        credential_provider: CredentialProvider | None = None,
    ) -> None: ...
    @property
    def keyfile(self) -> str | None: ...
    @property
    def certfile(self) -> str | None: ...
    @property
    def cert_reqs(self) -> ssl.VerifyMode: ...
    @property
    def ca_certs(self) -> str | None: ...
    @property
    def ca_data(self) -> str | None: ...
    @property
    def check_hostname(self) -> bool: ...

class RedisSSLContext:
    keyfile: str | None
    certfile: str | None
    cert_reqs: ssl.VerifyMode
    ca_certs: str | None
    ca_data: str | None
    check_hostname: bool
    context: ssl.SSLContext | None
    def __init__(
        self,
        keyfile: str | None = None,
        certfile: str | None = None,
        cert_reqs: _SSLVerifyMode | None = None,
        ca_certs: str | None = None,
        ca_data: str | None = None,
        check_hostname: bool = False,
    ) -> None: ...
    def get(self) -> ssl.SSLContext: ...

class UnixDomainSocketConnection(Connection):
    path: str
    def __init__(
        self,
        *,
        path: str = "",
        # **kwargs forwarded to AbstractConnection.
        db: str | int = 0,
        password: str | None = None,
        socket_timeout: float | None = None,
        socket_connect_timeout: float | None = None,
        retry_on_timeout: bool = False,
        retry_on_error: list[type[RedisError]] | _Sentinel = ...,
        encoding: str = "utf-8",
        encoding_errors: str = "strict",
        decode_responses: bool = False,
        parser_class: type[BaseParser] = ...,
        socket_read_size: int = 65536,
        health_check_interval: float = 0,
        client_name: str | None = None,
        username: str | None = None,
        retry: Retry | None = None,
        redis_connect_func: ConnectCallbackT | None = None,
        encoder_class: type[Encoder] = ...,
        credential_provider: CredentialProvider | None = None,
    ) -> None: ...
    def repr_pieces(self) -> list[tuple[str, Any]]: ...

FALSE_STRINGS: Final[tuple[str, ...]]

def to_bool(value: object) -> bool | None: ...

URL_QUERY_ARGUMENT_PARSERS: MappingProxyType[str, Callable[[str], Any]]

class ConnectKwargs(TypedDict):
    username: str
    password: str
    connection_class: type[AbstractConnection]
    host: str
    port: int
    db: int
    path: str

def parse_url(url: str) -> ConnectKwargs: ...

_ConnectionT = TypeVar("_ConnectionT", bound=AbstractConnection)

class ConnectionPool(Generic[_ConnectionT]):
    # kwargs accepts all arguments from the connection class chosen for
    # the given URL, except those encoded in the URL itself.
    @classmethod
    def from_url(cls, url: str, **kwargs: Any) -> Self: ...

    connection_class: type[_ConnectionT]
    connection_kwargs: Mapping[str, Any]
    max_connections: int
    encoder_class: type[Encoder]
    pid: int

    @overload
    def __init__(
        self: ConnectionPool[_ConnectionT],  # pyright: ignore[reportInvalidTypeVarUse]  #11780
        connection_class: type[_ConnectionT],
        max_connections: int | None = None,
        # **kwargs are passed to the constructed connection instances.
        **connection_kwargs: Any,
    ) -> None: ...
    @overload
    def __init__(self: ConnectionPool[Connection], *, max_connections: int | None = None, **connection_kwargs) -> None: ...
    def reset(self) -> None: ...
    async def get_connection(self, command_name: Unused, *keys: Unused, **options: Unused) -> _ConnectionT: ...
    def get_encoder(self) -> Encoder: ...
    def make_connection(self) -> _ConnectionT: ...
    async def release(self, connection: AbstractConnection) -> None: ...
    def owns_connection(self, connection: AbstractConnection) -> bool: ...
    async def disconnect(self, inuse_connections: bool = True) -> None: ...
    def set_retry(self, retry: Retry) -> None: ...

class BlockingConnectionPool(ConnectionPool[_ConnectionT]):
    queue_class: type[asyncio.Queue[_ConnectionT | None]]
    timeout: int | None
    pool: asyncio.Queue[_ConnectionT | None]

    @overload
    def __init__(
        self: BlockingConnectionPool[_ConnectionT],  # pyright: ignore[reportInvalidTypeVarUse]  #11780
        max_connections: int,
        timeout: int | None,
        connection_class: type[_ConnectionT],
        queue_class: type[asyncio.Queue[_ConnectionT | None]] = ...,
        # **kwargs are passed to the constructed connection instances.
        **connection_kwargs: Any,
    ) -> None: ...
    @overload
    def __init__(
        self: BlockingConnectionPool[_ConnectionT],  # pyright: ignore[reportInvalidTypeVarUse]  #11780
        max_connections: int = 50,
        timeout: int | None = 20,
        *,
        connection_class: type[_ConnectionT],
        queue_class: type[asyncio.Queue[_ConnectionT | None]] = ...,
        # **kwargs are passed to the constructed connection instances.
        **connection_kwargs: Any,
    ) -> None: ...
    @overload
    def __init__(
        self: BlockingConnectionPool[Connection],
        max_connections: int = 50,
        timeout: int | None = 20,
        *,
        queue_class: type[asyncio.Queue[Connection | None]] = ...,
        # **kwargs are passed to the constructed connection instances.
        **connection_kwargs: Any,
    ) -> None: ...
