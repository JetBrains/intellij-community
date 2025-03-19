from _typeshed import Incomplete

import urllib3
import urllib3.connection
from docker.transport.basehttpadapter import BaseHTTPAdapter

RecentlyUsedContainer: Incomplete

class NpipeHTTPConnection(urllib3.connection.HTTPConnection):
    npipe_path: Incomplete
    timeout: Incomplete
    def __init__(self, npipe_path, timeout: int = 60) -> None: ...
    sock: Incomplete
    def connect(self) -> None: ...

class NpipeHTTPConnectionPool(urllib3.connectionpool.HTTPConnectionPool):
    npipe_path: Incomplete
    timeout: Incomplete
    def __init__(self, npipe_path, timeout: int = 60, maxsize: int = 10) -> None: ...

class NpipeHTTPAdapter(BaseHTTPAdapter):
    __attrs__: Incomplete
    npipe_path: Incomplete
    timeout: Incomplete
    max_pool_size: Incomplete
    pools: Incomplete
    def __init__(self, base_url, timeout: int = 60, pool_connections=..., max_pool_size=...) -> None: ...
    def get_connection(self, url, proxies: Incomplete | None = None): ...
    def request_url(self, request, proxies): ...
