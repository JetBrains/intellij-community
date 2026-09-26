from _typeshed import Incomplete
from collections.abc import Mapping
from ssl import SSLContext
from typing import Any, Literal, TypedDict, type_check_only
from typing_extensions import NotRequired, deprecated

import urllib3
from urllib3.connectionpool import ConnectionPool
from urllib3.contrib.socks import SOCKSProxyManager as SOCKSProxyManager
from urllib3.exceptions import (
    ConnectTimeoutError as ConnectTimeoutError,
    MaxRetryError as MaxRetryError,
    ProtocolError as ProtocolError,
    ReadTimeoutError as ReadTimeoutError,
    ResponseError as ResponseError,
)
from urllib3.poolmanager import PoolManager as PoolManager, proxy_from_url as proxy_from_url
from urllib3.util.retry import Retry as Retry

from .cookies import extract_cookies_to_jar as extract_cookies_to_jar
from .exceptions import (
    ConnectionError as ConnectionError,
    ConnectTimeout as ConnectTimeout,
    ProxyError as ProxyError,
    ReadTimeout as ReadTimeout,
    RetryError as RetryError,
    SSLError as SSLError,
)
from .models import PreparedRequest, Response as Response
from .structures import CaseInsensitiveDict as CaseInsensitiveDict
from .utils import (
    DEFAULT_CA_BUNDLE_PATH as DEFAULT_CA_BUNDLE_PATH,
    _Uri,
    get_auth_from_url as get_auth_from_url,
    get_encoding_from_headers as get_encoding_from_headers,
    prepend_scheme_if_needed as prepend_scheme_if_needed,
    urldefragauth as urldefragauth,
)

# Arguments to urllib3 connection_from_host() functions (except pool_kwargs).
@type_check_only
class _HostParams(TypedDict):
    host: str
    scheme: str
    port: int

@type_check_only
class _PoolKwargs(TypedDict):
    ssl_context: NotRequired[SSLContext]
    ca_certs: NotRequired[str]
    ca_cert_dir: NotRequired[str]
    cert_reqs: Literal["CERT_REQUIRED", "CERT_NONE"]
    cert_file: NotRequired[str]
    key_file: NotRequired[str]

DEFAULT_POOLBLOCK: bool
DEFAULT_POOLSIZE: int
DEFAULT_RETRIES: int
DEFAULT_POOL_TIMEOUT: float | None

class BaseAdapter:
    def __init__(self) -> None: ...
    def send(
        self,
        request: PreparedRequest,
        stream: bool = False,
        timeout: None | float | tuple[float, float] | tuple[float, None] = None,
        verify: bool | str = True,
        cert: None | bytes | str | tuple[bytes | str, bytes | str] = None,
        proxies: Mapping[str, str] | None = None,
    ) -> Response: ...
    def close(self) -> None: ...

class HTTPAdapter(BaseAdapter):
    __attrs__: Incomplete
    max_retries: Retry
    config: Incomplete
    proxy_manager: Incomplete
    def __init__(
        self, pool_connections: int = 10, pool_maxsize: int = 10, max_retries: Retry | int | None = 0, pool_block: bool = False
    ) -> None: ...
    poolmanager: Incomplete
    def init_poolmanager(
        self,
        connections: int,
        maxsize: int,
        block: bool = False,
        **pool_kwargs: Any,  # Any: Arbitrary keyword arguments passed directly to urllib3's PoolManager constructor.
        # Allowed types depend on urllib3 version, but typically include:
        # ssl_version (int), cert_reqs (str), ca_certs (str), ca_cert_dir (str),
        # ssl_context (ssl.SSLContext), socket_options (list), etc.
        # We use Any because the exact set is dynamic and not fully specified in stubs.
    ) -> None: ...
    def proxy_manager_for(
        self,
        proxy: str,
        **proxy_kwargs: Any,  # Any: Same as pool_kwargs above, passed to ProxyManager or SOCKSProxyManager.
        # May include: ssl_context, cert_reqs, ca_certs, ca_cert_dir, etc.
    ) -> Any:  # Any: Returns either urllib3.ProxyManager (for HTTP/HTTPS proxies) or SOCKSProxyManager (for SOCKS).
        # The exact return type depends on the proxy scheme and is not needed by callers; using Any avoids
        # circular imports or complex union types. In practice, the object adheres to a common interface.
        ...

    def cert_verify(self, conn, url, verify, cert): ...
    def build_response(self, req: PreparedRequest, resp: urllib3.BaseHTTPResponse) -> Response: ...
    def build_connection_pool_key_attributes(
        self, request: PreparedRequest, verify: bool | str, cert: str | tuple[str, str] | None = None
    ) -> tuple[_HostParams, _PoolKwargs]: ...
    def get_connection_with_tls_context(
        self,
        request: PreparedRequest,
        verify: bool | str | None,
        proxies: Mapping[str, str] | None = None,
        cert: tuple[str, str] | str | None = None,
    ) -> ConnectionPool: ...
    @deprecated("Use get_connection_with_tls_context() instead.")
    def get_connection(self, url: _Uri, proxies: Mapping[str, str] | None = None) -> ConnectionPool: ...
    def close(self) -> None: ...
    def request_url(self, request: PreparedRequest, proxies: Mapping[str, str] | None) -> str: ...
    def add_headers(
        self,
        request: PreparedRequest,
        **kwargs: Any,  # Any: Hook method for subclasses to add custom headers.
        # The kwargs mirror the send() parameters: stream (bool), timeout (float|tuple),
        # verify (bool|str), cert (str|tuple), proxies (dict). Base implementation ignores them.
        # Using Any allows subclasses to access these arguments without repeating the full signature.
    ) -> None: ...
    def proxy_headers(self, proxy: str) -> dict[str, str]: ...
    def send(
        self,
        request: PreparedRequest,
        stream: bool = False,
        timeout: None | float | tuple[float, float] | tuple[float, None] = None,
        verify: bool | str = True,
        cert: None | bytes | str | tuple[bytes | str, bytes | str] = None,
        proxies: Mapping[str, str] | None = None,
    ) -> Response: ...
