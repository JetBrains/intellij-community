import socket as _socket
from typing import Final

from kafka.net.inet import KafkaNetSocket

class ProxyConnectionStates:
    DISCONNECTED: Final = "<disconnected>"
    CONNECTING: Final = "<connecting>"
    NEGOTIATE_PROPOSE: Final = "<negotiate_propose>"
    NEGOTIATING: Final = "<negotiating>"
    AUTHENTICATING: Final = "<authenticating>"
    REQUEST_SUBMIT: Final = "<request_submit>"
    REQUESTING: Final = "<requesting>"
    READ_ADDRESS: Final = "<read_address>"
    COMPLETE: Final = "<complete>"

class Socks5Proxy(KafkaNetSocket):
    SCHEMES: tuple[str, ...]
    def __init__(self, proxy_url: str) -> None: ...  # pyright: ignore[reportInconsistentConstructor]
    def dns_lookup(self, host, port, proxy: bool = False): ...
    def socket(self, family=..., sock_type=..., proto=...) -> _socket.socket: ...
    def connect_ex(self, sock, addr) -> int: ...
