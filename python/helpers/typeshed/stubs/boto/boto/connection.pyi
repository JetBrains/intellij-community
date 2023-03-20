from typing import Any

from six.moves import http_client

HAVE_HTTPS_CONNECTION: bool
ON_APP_ENGINE: Any
PORTS_BY_SECURITY: Any
DEFAULT_CA_CERTS_FILE: Any

class HostConnectionPool:
    queue: Any
    def __init__(self) -> None: ...
    def size(self): ...
    def put(self, conn): ...
    def get(self): ...
    def clean(self): ...

class ConnectionPool:
    CLEAN_INTERVAL: float
    STALE_DURATION: float
    host_to_pool: Any
    last_clean_time: float
    mutex: Any
    def __init__(self) -> None: ...
    def size(self): ...
    def get_http_connection(self, host, port, is_secure): ...
    def put_http_connection(self, host, port, is_secure, conn): ...
    def clean(self): ...

class HTTPRequest:
    method: Any
    protocol: Any
    host: Any
    port: Any
    path: Any
    auth_path: Any
    params: Any
    headers: Any
    body: Any
    def __init__(self, method, protocol, host, port, path, auth_path, params, headers, body) -> None: ...
    def authorize(self, connection, **kwargs): ...

class HTTPResponse(http_client.HTTPResponse):
    def __init__(self, *args, **kwargs) -> None: ...
    def read(self, amt: Any | None = ...): ...

class AWSAuthConnection:
    suppress_consec_slashes: Any
    num_retries: int
    is_secure: Any
    https_validate_certificates: Any
    ca_certificates_file: Any
    port: Any
    http_exceptions: Any
    http_unretryable_exceptions: Any
    socket_exception_values: Any
    https_connection_factory: Any
    protocol: str
    host: Any
    path: Any
    debug: Any
    host_header: Any
    http_connection_kwargs: Any
    provider: Any
    auth_service_name: Any
    request_hook: Any
    def __init__(
        self,
        host,
        aws_access_key_id: Any | None = ...,
        aws_secret_access_key: Any | None = ...,
        is_secure: bool = ...,
        port: Any | None = ...,
        proxy: Any | None = ...,
        proxy_port: Any | None = ...,
        proxy_user: Any | None = ...,
        proxy_pass: Any | None = ...,
        debug: int = ...,
        https_connection_factory: Any | None = ...,
        path: str = ...,
        provider: str = ...,
        security_token: Any | None = ...,
        suppress_consec_slashes: bool = ...,
        validate_certs: bool = ...,
        profile_name: Any | None = ...,
    ) -> None: ...
    auth_region_name: Any
    @property
    def connection(self): ...
    @property
    def aws_access_key_id(self): ...
    @property
    def gs_access_key_id(self) -> Any: ...
    @property
    def access_key(self): ...
    @property
    def aws_secret_access_key(self): ...
    @property
    def gs_secret_access_key(self): ...
    @property
    def secret_key(self): ...
    @property
    def profile_name(self): ...
    def get_path(self, path: str = ...): ...
    def server_name(self, port: Any | None = ...): ...
    proxy: Any
    proxy_port: Any
    proxy_user: Any
    proxy_pass: Any
    no_proxy: Any
    use_proxy: Any
    def handle_proxy(self, proxy, proxy_port, proxy_user, proxy_pass): ...
    def get_http_connection(self, host, port, is_secure): ...
    def skip_proxy(self, host): ...
    def new_http_connection(self, host, port, is_secure): ...
    def put_http_connection(self, host, port, is_secure, connection): ...
    def proxy_ssl(self, host: Any | None = ..., port: Any | None = ...): ...
    def prefix_proxy_to_path(self, path, host: Any | None = ...): ...
    def get_proxy_auth_header(self): ...
    def get_proxy_url_with_auth(self): ...
    def set_host_header(self, request): ...
    def set_request_hook(self, hook): ...
    def build_base_http_request(
        self,
        method,
        path,
        auth_path,
        params: Any | None = ...,
        headers: Any | None = ...,
        data: str = ...,
        host: Any | None = ...,
    ): ...
    def make_request(
        self,
        method,
        path,
        headers: Any | None = ...,
        data: str = ...,
        host: Any | None = ...,
        auth_path: Any | None = ...,
        sender: Any | None = ...,
        override_num_retries: Any | None = ...,
        params: Any | None = ...,
        retry_handler: Any | None = ...,
    ): ...
    def close(self): ...

class AWSQueryConnection(AWSAuthConnection):
    APIVersion: str
    ResponseError: Any
    def __init__(
        self,
        aws_access_key_id: Any | None = ...,
        aws_secret_access_key: Any | None = ...,
        is_secure: bool = ...,
        port: Any | None = ...,
        proxy: Any | None = ...,
        proxy_port: Any | None = ...,
        proxy_user: Any | None = ...,
        proxy_pass: Any | None = ...,
        host: Any | None = ...,
        debug: int = ...,
        https_connection_factory: Any | None = ...,
        path: str = ...,
        security_token: Any | None = ...,
        validate_certs: bool = ...,
        profile_name: Any | None = ...,
        provider: str = ...,
    ) -> None: ...
    def get_utf8_value(self, value): ...
    def make_request(  # type: ignore[override]
        self, action, params: Any | None = ..., path: str = ..., verb: str = ..., *args, **kwargs
    ): ...
    def build_list_params(self, params, items, label): ...
    def build_complex_list_params(self, params, items, label, names): ...
    def get_list(self, action, params, markers, path: str = ..., parent: Any | None = ..., verb: str = ...): ...
    def get_object(self, action, params, cls, path: str = ..., parent: Any | None = ..., verb: str = ...): ...
    def get_status(self, action, params, path: str = ..., parent: Any | None = ..., verb: str = ...): ...
