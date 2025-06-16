import socket
from _typeshed import Incomplete

import urllib3
import urllib3.connection
from docker.transport.basehttpadapter import BaseHTTPAdapter

RecentlyUsedContainer: Incomplete

class SSHSocket(socket.socket):
    host: Incomplete
    port: Incomplete
    user: Incomplete
    proc: Incomplete
    def __init__(self, host) -> None: ...
    def connect(self, **kwargs) -> None: ...  # type:ignore[override]
    def sendall(self, data) -> None: ...  # type:ignore[override]
    def send(self, data): ...  # type:ignore[override]
    def recv(self, n): ...  # type:ignore[override]
    def makefile(self, mode): ...  # type:ignore[override]
    def close(self) -> None: ...

class SSHConnection(urllib3.connection.HTTPConnection):
    ssh_transport: Incomplete
    timeout: Incomplete
    ssh_host: Incomplete
    def __init__(self, ssh_transport=None, timeout: int = 60, host=None) -> None: ...
    sock: Incomplete
    def connect(self) -> None: ...

class SSHConnectionPool(urllib3.connectionpool.HTTPConnectionPool):
    scheme: str
    ssh_transport: Incomplete
    timeout: Incomplete
    ssh_host: Incomplete
    def __init__(self, ssh_client=None, timeout: int = 60, maxsize: int = 10, host=None) -> None: ...

class SSHHTTPAdapter(BaseHTTPAdapter):
    __attrs__: Incomplete
    ssh_client: Incomplete
    ssh_host: Incomplete
    timeout: Incomplete
    max_pool_size: Incomplete
    pools: Incomplete
    def __init__(self, base_url, timeout: int = 60, pool_connections=..., max_pool_size=..., shell_out: bool = False) -> None: ...
    def get_connection(self, url, proxies=None): ...
    def close(self) -> None: ...
