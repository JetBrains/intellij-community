import http.client
from _typeshed import Self
from collections.abc import Generator
from typing import Any

from .error import *

__author__: str
__copyright__: str
__contributors__: list[str]
__license__: str
__version__: str

debuglevel: int
RETRIES: int

class Authentication:
    path: Any
    host: Any
    credentials: Any
    http: Any
    def __init__(self, credentials, host, request_uri, headers, response, content, http) -> None: ...
    def depth(self, request_uri): ...
    def inscope(self, host, request_uri): ...
    def request(self, method, request_uri, headers, content) -> None: ...
    def response(self, response, content): ...
    def __eq__(self, auth): ...
    def __ne__(self, auth): ...
    def __lt__(self, auth): ...
    def __gt__(self, auth): ...
    def __le__(self, auth): ...
    def __ge__(self, auth): ...
    def __bool__(self): ...

class BasicAuthentication(Authentication):
    def __init__(self, credentials, host, request_uri, headers, response, content, http) -> None: ...
    def request(self, method, request_uri, headers, content) -> None: ...

class DigestAuthentication(Authentication):
    challenge: Any
    A1: Any
    def __init__(self, credentials, host, request_uri, headers, response, content, http) -> None: ...
    def request(self, method, request_uri, headers, content, cnonce: Any | None = ...): ...
    def response(self, response, content): ...

class HmacDigestAuthentication(Authentication):
    challenge: Any
    hashmod: Any
    pwhashmod: Any
    key: Any
    def __init__(self, credentials, host, request_uri, headers, response, content, http) -> None: ...
    def request(self, method, request_uri, headers, content) -> None: ...
    def response(self, response, content): ...

class WsseAuthentication(Authentication):
    def __init__(self, credentials, host, request_uri, headers, response, content, http) -> None: ...
    def request(self, method, request_uri, headers, content) -> None: ...

class GoogleLoginAuthentication(Authentication):
    Auth: str
    def __init__(self, credentials, host, request_uri, headers, response, content, http) -> None: ...
    def request(self, method, request_uri, headers, content) -> None: ...

class FileCache:
    cache: Any
    safe: Any
    def __init__(self, cache, safe=...) -> None: ...
    def get(self, key): ...
    def set(self, key, value) -> None: ...
    def delete(self, key) -> None: ...

class Credentials:
    credentials: Any
    def __init__(self) -> None: ...
    def add(self, name, password, domain: str = ...) -> None: ...
    def clear(self) -> None: ...
    def iter(self, domain) -> Generator[tuple[str, str], None, None]: ...

class KeyCerts(Credentials):
    def add(self, key, cert, domain, password) -> None: ...  # type: ignore[override]
    def iter(self, domain) -> Generator[tuple[str, str, str], None, None]: ...  # type: ignore[override]

class AllHosts: ...

class ProxyInfo:
    bypass_hosts: Any
    def __init__(
        self,
        proxy_type,
        proxy_host,
        proxy_port,
        proxy_rdns: bool = ...,
        proxy_user: Any | None = ...,
        proxy_pass: Any | None = ...,
        proxy_headers: Any | None = ...,
    ) -> None: ...
    def astuple(self): ...
    def isgood(self): ...
    def applies_to(self, hostname): ...
    def bypass_host(self, hostname): ...

class HTTPConnectionWithTimeout(http.client.HTTPConnection):
    proxy_info: Any
    def __init__(self, host, port: Any | None = ..., timeout: Any | None = ..., proxy_info: Any | None = ...) -> None: ...
    sock: Any
    def connect(self) -> None: ...

class HTTPSConnectionWithTimeout(http.client.HTTPSConnection):
    disable_ssl_certificate_validation: Any
    ca_certs: Any
    proxy_info: Any
    key_file: Any
    cert_file: Any
    key_password: Any
    def __init__(
        self,
        host,
        port: Any | None = ...,
        key_file: Any | None = ...,
        cert_file: Any | None = ...,
        timeout: Any | None = ...,
        proxy_info: Any | None = ...,
        ca_certs: Any | None = ...,
        disable_ssl_certificate_validation: bool = ...,
        tls_maximum_version: Any | None = ...,
        tls_minimum_version: Any | None = ...,
        key_password: Any | None = ...,
    ) -> None: ...
    sock: Any
    def connect(self) -> None: ...

class Http:
    proxy_info: Any
    ca_certs: Any
    disable_ssl_certificate_validation: Any
    tls_maximum_version: Any
    tls_minimum_version: Any
    connections: Any
    cache: Any
    credentials: Any
    certificates: Any
    authorizations: Any
    follow_redirects: bool
    redirect_codes: Any
    optimistic_concurrency_methods: Any
    safe_methods: Any
    follow_all_redirects: bool
    ignore_etag: bool
    force_exception_to_status_code: bool
    timeout: Any
    forward_authorization_headers: bool
    def __init__(
        self,
        cache: Any | None = ...,
        timeout: Any | None = ...,
        proxy_info=...,
        ca_certs: Any | None = ...,
        disable_ssl_certificate_validation: bool = ...,
        tls_maximum_version: Any | None = ...,
        tls_minimum_version: Any | None = ...,
    ) -> None: ...
    def close(self) -> None: ...
    def add_credentials(self, name, password, domain: str = ...) -> None: ...
    def add_certificate(self, key, cert, domain, password: Any | None = ...) -> None: ...
    def clear_credentials(self) -> None: ...
    def request(
        self,
        uri,
        method: str = ...,
        body: Any | None = ...,
        headers: Any | None = ...,
        redirections=...,
        connection_type: Any | None = ...,
    ): ...

class Response(dict[str, Any]):
    fromcache: bool
    version: int
    status: int
    reason: str
    previous: Any
    def __init__(self, info) -> None: ...
    @property
    def dict(self: Self) -> Self: ...
