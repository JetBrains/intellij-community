from _typeshed.wsgi import StartResponse, WSGIApplication, WSGIEnvironment
from typing import Iterable

class ProxyFix(object):
    app: WSGIApplication
    x_for: int
    x_proto: int
    x_host: int
    x_port: int
    x_prefix: int
    num_proxies: int
    def __init__(
        self,
        app: WSGIApplication,
        num_proxies: int | None = ...,
        x_for: int = ...,
        x_proto: int = ...,
        x_host: int = ...,
        x_port: int = ...,
        x_prefix: int = ...,
    ) -> None: ...
    def get_remote_addr(self, forwarded_for: Iterable[str]) -> str | None: ...
    def __call__(self, environ: WSGIEnvironment, start_response: StartResponse) -> Iterable[bytes]: ...
