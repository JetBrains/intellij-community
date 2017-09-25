from typing import Any
from tornado.util import Configurable

class HTTPClient:
    def __init__(self, async_client_class=..., **kwargs) -> None: ...
    def __del__(self): ...
    def close(self): ...
    def fetch(self, request, **kwargs): ...

class AsyncHTTPClient(Configurable):
    @classmethod
    def configurable_base(cls): ...
    @classmethod
    def configurable_default(cls): ...
    def __new__(cls, io_loop=..., force_instance=..., **kwargs): ...
    io_loop = ...  # type: Any
    defaults = ...  # type: Any
    def initialize(self, io_loop, defaults=...): ...
    def close(self): ...
    def fetch(self, request, callback=..., raise_error=..., **kwargs): ...
    def fetch_impl(self, request, callback): ...
    @classmethod
    def configure(cls, impl, **kwargs): ...

class HTTPRequest:
    headers = ...  # type: Any
    proxy_host = ...  # type: Any
    proxy_port = ...  # type: Any
    proxy_username = ...  # type: Any
    proxy_password = ...  # type: Any
    url = ...  # type: Any
    method = ...  # type: Any
    body = ...  # type: Any
    body_producer = ...  # type: Any
    auth_username = ...  # type: Any
    auth_password = ...  # type: Any
    auth_mode = ...  # type: Any
    connect_timeout = ...  # type: Any
    request_timeout = ...  # type: Any
    follow_redirects = ...  # type: Any
    max_redirects = ...  # type: Any
    user_agent = ...  # type: Any
    decompress_response = ...  # type: Any
    network_interface = ...  # type: Any
    streaming_callback = ...  # type: Any
    header_callback = ...  # type: Any
    prepare_curl_callback = ...  # type: Any
    allow_nonstandard_methods = ...  # type: Any
    validate_cert = ...  # type: Any
    ca_certs = ...  # type: Any
    allow_ipv6 = ...  # type: Any
    client_key = ...  # type: Any
    client_cert = ...  # type: Any
    ssl_options = ...  # type: Any
    expect_100_continue = ...  # type: Any
    start_time = ...  # type: Any
    def __init__(self, url, method=..., headers=..., body=..., auth_username=..., auth_password=..., auth_mode=..., connect_timeout=..., request_timeout=..., if_modified_since=..., follow_redirects=..., max_redirects=..., user_agent=..., use_gzip=..., network_interface=..., streaming_callback=..., header_callback=..., prepare_curl_callback=..., proxy_host=..., proxy_port=..., proxy_username=..., proxy_password=..., allow_nonstandard_methods=..., validate_cert=..., ca_certs=..., allow_ipv6=..., client_key=..., client_cert=..., body_producer=..., expect_100_continue=..., decompress_response=..., ssl_options=...) -> None: ...
    @property
    def headers(self): ...
    @headers.setter
    def headers(self, value): ...
    @property
    def body(self): ...
    @body.setter
    def body(self, value): ...
    @property
    def body_producer(self): ...
    @body_producer.setter
    def body_producer(self, value): ...
    @property
    def streaming_callback(self): ...
    @streaming_callback.setter
    def streaming_callback(self, value): ...
    @property
    def header_callback(self): ...
    @header_callback.setter
    def header_callback(self, value): ...
    @property
    def prepare_curl_callback(self): ...
    @prepare_curl_callback.setter
    def prepare_curl_callback(self, value): ...

class HTTPResponse:
    request = ...  # type: Any
    code = ...  # type: Any
    reason = ...  # type: Any
    headers = ...  # type: Any
    buffer = ...  # type: Any
    effective_url = ...  # type: Any
    error = ...  # type: Any
    request_time = ...  # type: Any
    time_info = ...  # type: Any
    def __init__(self, request, code, headers=..., buffer=..., effective_url=..., error=..., request_time=..., time_info=..., reason=...) -> None: ...
    body = ...  # type: Any
    def rethrow(self): ...

class HTTPError(Exception):
    code = ...  # type: Any
    response = ...  # type: Any
    def __init__(self, code, message=..., response=...) -> None: ...

class _RequestProxy:
    request = ...  # type: Any
    defaults = ...  # type: Any
    def __init__(self, request, defaults) -> None: ...
    def __getattr__(self, name): ...

def main(): ...
