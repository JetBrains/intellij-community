import socket
from _typeshed import Incomplete, ReadableBuffer
from types import ModuleType
from typing import Final, Literal
from typing_extensions import TypeAlias

_ProxyType: TypeAlias = Literal[1, 2, 3, 4]

PROXY_TYPE_SOCKS4: Final[_ProxyType]
PROXY_TYPE_SOCKS5: Final[_ProxyType]
PROXY_TYPE_HTTP: Final[_ProxyType]
PROXY_TYPE_HTTP_NO_TUNNEL: Final[_ProxyType]

class ProxyError(Exception): ...
class GeneralProxyError(ProxyError): ...
class Socks5AuthError(ProxyError): ...
class Socks5Error(ProxyError): ...
class Socks4Error(ProxyError): ...
class HTTPError(ProxyError): ...

def setdefaultproxy(
    proxytype: _ProxyType | None = None,
    addr: str | None = None,
    port: int | None = None,
    rdns: bool = True,
    username: str | None = None,
    password: str | None = None,
) -> None: ...
def wrapmodule(module: ModuleType) -> None: ...

class socksocket(socket.socket):
    def __init__(
        self,
        family: socket.AddressFamily | int = ...,
        type: socket.SocketKind | int = ...,
        proto: int = 0,
        _sock: int | None = None,
    ) -> None: ...
    def sendall(self, content: ReadableBuffer, flags: int = ...) -> None: ...  # type: ignore[override]
    def setproxy(
        self,
        proxytype: _ProxyType | None = None,
        addr: str | None = None,
        port: int | None = None,
        rdns: bool = True,
        username: str | None = None,
        password: str | None = None,
        headers: dict[str, str] | None = None,
    ) -> None: ...
    def getproxysockname(self) -> tuple[str | bytes, Incomplete] | None: ...
    def getproxypeername(self) -> socket._RetAddress: ...
    def getpeername(self) -> tuple[str | bytes, Incomplete] | None: ...
    def connect(self, destpair: list[str | bytes | int] | tuple[str | bytes, int]) -> None: ...  # type: ignore[override]
