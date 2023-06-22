from collections.abc import Mapping
from typing import Any

from urllib3.contrib.socks import SOCKSProxyManager as SOCKSProxyManager
from urllib3.exceptions import (
    ConnectTimeoutError as ConnectTimeoutError,
    MaxRetryError as MaxRetryError,
    ProtocolError as ProtocolError,
    ReadTimeoutError as ReadTimeoutError,
    ResponseError as ResponseError,
)
from urllib3.poolmanager import PoolManager as PoolManager, proxy_from_url as proxy_from_url
from urllib3.response import HTTPResponse as HTTPResponse
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
    get_auth_from_url as get_auth_from_url,
    get_encoding_from_headers as get_encoding_from_headers,
    prepend_scheme_if_needed as prepend_scheme_if_needed,
    urldefragauth as urldefragauth,
)

DEFAULT_POOLBLOCK: bool
DEFAULT_POOLSIZE: int
DEFAULT_RETRIES: int
DEFAULT_POOL_TIMEOUT: float | None

class BaseAdapter:
    def __init__(self) -> None: ...
    def send(
        self,
        request: PreparedRequest,
        stream: bool = ...,
        timeout: None | float | tuple[float, float] | tuple[float, None] = ...,
        verify: bool | str = ...,
        cert: None | bytes | str | tuple[bytes | str, bytes | str] = ...,
        proxies: Mapping[str, str] | None = ...,
    ) -> Response: ...
    def close(self) -> None: ...

class HTTPAdapter(BaseAdapter):
    __attrs__: Any
    max_retries: Retry
    config: Any
    proxy_manager: Any
    def __init__(
        self, pool_connections: int = ..., pool_maxsize: int = ..., max_retries: Retry | int | None = ..., pool_block: bool = ...
    ) -> None: ...
    poolmanager: Any
    def init_poolmanager(self, connections, maxsize, block=..., **pool_kwargs): ...
    def proxy_manager_for(self, proxy, **proxy_kwargs): ...
    def cert_verify(self, conn, url, verify, cert): ...
    def build_response(self, req, resp): ...
    def get_connection(self, url, proxies=...): ...
    def close(self): ...
    def request_url(self, request, proxies): ...
    def add_headers(self, request, **kwargs): ...
    def proxy_headers(self, proxy): ...
    def send(
        self,
        request: PreparedRequest,
        stream: bool = ...,
        timeout: None | float | tuple[float, float] | tuple[float, None] = ...,
        verify: bool | str = ...,
        cert: None | bytes | str | tuple[bytes | str, bytes | str] = ...,
        proxies: Mapping[str, str] | None = ...,
    ) -> Response: ...
