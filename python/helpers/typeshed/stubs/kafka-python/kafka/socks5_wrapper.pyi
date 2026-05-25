from _typeshed import Incomplete

log: Incomplete

class ProxyConnectionStates:
    DISCONNECTED: str
    CONNECTING: str
    NEGOTIATE_PROPOSE: str
    NEGOTIATING: str
    AUTHENTICATING: str
    REQUEST_SUBMIT: str
    REQUESTING: str
    READ_ADDRESS: str
    COMPLETE: str

class Socks5Wrapper:
    def __init__(self, proxy_url, afi) -> None: ...
    @classmethod
    def is_inet_4_or_6(cls, gai): ...
    @classmethod
    def dns_lookup(cls, host, port, afi=...): ...
    @classmethod
    def use_remote_lookup(cls, proxy_url): ...
    def socket(self, family, sock_type): ...
    def connect_ex(self, addr): ...
