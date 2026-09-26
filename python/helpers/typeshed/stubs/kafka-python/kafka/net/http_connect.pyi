import socket as _socket

from kafka.net.inet import KafkaNetSocket

class HttpConnectProxy(KafkaNetSocket):
    SCHEMES: tuple[str, ...]
    def __init__(self, proxy_url) -> None: ...  # pyright: ignore[reportInconsistentConstructor]
    def dns_lookup(self, host, port, proxy: bool = False): ...
    def socket(self, family=..., sock_type=..., proto=...) -> _socket.socket: ...
    def connect_ex(self, sock, addr) -> int: ...
