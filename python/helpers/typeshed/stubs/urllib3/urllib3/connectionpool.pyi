import queue
from _typeshed import Self
from collections.abc import Mapping
from logging import Logger
from types import TracebackType
from typing import Any, ClassVar
from typing_extensions import Literal, TypeAlias

from . import connection, exceptions, request, response
from .connection import BaseSSLError as BaseSSLError, ConnectionError as ConnectionError, HTTPException as HTTPException
from .packages import ssl_match_hostname
from .util import Url, connection as _connection, queue as urllib3queue, retry, timeout, url

ClosedPoolError = exceptions.ClosedPoolError
ProtocolError = exceptions.ProtocolError
EmptyPoolError = exceptions.EmptyPoolError
HostChangedError = exceptions.HostChangedError
LocationValueError = exceptions.LocationValueError
MaxRetryError = exceptions.MaxRetryError
ProxyError = exceptions.ProxyError
ReadTimeoutError = exceptions.ReadTimeoutError
SSLError = exceptions.SSLError
TimeoutError = exceptions.TimeoutError
InsecureRequestWarning = exceptions.InsecureRequestWarning
CertificateError = ssl_match_hostname.CertificateError
port_by_scheme = connection.port_by_scheme
DummyConnection = connection.DummyConnection
HTTPConnection = connection.HTTPConnection
HTTPSConnection = connection.HTTPSConnection
VerifiedHTTPSConnection = connection.VerifiedHTTPSConnection
RequestMethods = request.RequestMethods
HTTPResponse = response.HTTPResponse
is_connection_dropped = _connection.is_connection_dropped
Retry = retry.Retry
Timeout = timeout.Timeout
get_host = url.get_host

_Timeout: TypeAlias = Timeout | float
_Retries: TypeAlias = Retry | bool | int

xrange: Any
log: Logger

class ConnectionPool:
    scheme: ClassVar[str | None]
    QueueCls: ClassVar[type[queue.Queue[Any]]]
    host: str
    port: int | None
    def __init__(self, host: str, port: int | None = ...) -> None: ...
    def __enter__(self: Self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_val: BaseException | None, exc_tb: TracebackType | None
    ) -> Literal[False]: ...
    def close(self) -> None: ...

class HTTPConnectionPool(ConnectionPool, RequestMethods):
    scheme: ClassVar[str]
    ConnectionCls: ClassVar[type[HTTPConnection | HTTPSConnection]]
    ResponseCls: ClassVar[type[HTTPResponse]]
    strict: bool
    timeout: _Timeout
    retries: _Retries | None
    pool: urllib3queue.LifoQueue | None
    block: bool
    proxy: Url | None
    proxy_headers: Mapping[str, str]
    num_connections: int
    num_requests: int
    conn_kw: Any
    def __init__(
        self,
        host: str,
        port: int | None = ...,
        strict: bool = ...,
        timeout: _Timeout = ...,
        maxsize: int = ...,
        block: bool = ...,
        headers: Mapping[str, str] | None = ...,
        retries: _Retries | None = ...,
        _proxy: Url | None = ...,
        _proxy_headers: Mapping[str, str] | None = ...,
        **conn_kw,
    ) -> None: ...
    def close(self) -> None: ...
    def is_same_host(self, url: str) -> bool: ...
    def urlopen(
        self,
        method,
        url,
        body=...,
        headers=...,
        retries=...,
        redirect=...,
        assert_same_host=...,
        timeout=...,
        pool_timeout=...,
        release_conn=...,
        **response_kw,
    ): ...

class HTTPSConnectionPool(HTTPConnectionPool):
    key_file: str | None
    cert_file: str | None
    cert_reqs: int | str | None
    ca_certs: str | None
    ssl_version: int | str | None
    assert_hostname: str | Literal[False] | None
    assert_fingerprint: str | None
    def __init__(
        self,
        host: str,
        port: int | None = ...,
        strict: bool = ...,
        timeout: _Timeout = ...,
        maxsize: int = ...,
        block: bool = ...,
        headers: Mapping[str, str] | None = ...,
        retries: _Retries | None = ...,
        _proxy: Url | None = ...,
        _proxy_headers: Mapping[str, str] | None = ...,
        key_file: str | None = ...,
        cert_file: str | None = ...,
        cert_reqs: int | str | None = ...,
        ca_certs: str | None = ...,
        ssl_version: int | str | None = ...,
        assert_hostname: str | Literal[False] | None = ...,
        assert_fingerprint: str | None = ...,
        **conn_kw,
    ) -> None: ...

def connection_from_url(url: str, **kw) -> HTTPConnectionPool: ...
