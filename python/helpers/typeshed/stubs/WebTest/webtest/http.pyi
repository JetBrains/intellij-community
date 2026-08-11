from _typeshed import Incomplete
from _typeshed.wsgi import StartResponse, WSGIApplication, WSGIEnvironment
from collections.abc import Iterable
from threading import Thread
from typing import Literal, TypeAlias
from typing_extensions import Self

from waitress.server import TcpWSGIServer

# NOTE: We may never really be able to complete this, since `create`
#       invokes `cls.__init__` which is exempt from LSP violations
#       unless we get something like `KwArgsOf[cls.__init__]`.
_WSGIServerParams: TypeAlias = Incomplete

def get_free_port() -> tuple[str, int]: ...
def check_server(host: str, port: int, path_info: str = "/", timeout: float = 3, retries: int = 30) -> int: ...

class StopableWSGIServer(TcpWSGIServer):
    was_shutdown: bool
    runner: Thread
    test_app: WSGIApplication
    application_url: str
    def wrapper(self, environ: WSGIEnvironment, start_response: StartResponse) -> Iterable[bytes]: ...
    def run(self) -> None: ...
    def shutdown(self, debug: bool = False) -> Literal[True]: ...
    # NOTE: This has the same keyword arguments as cls.__init__, which
    #       we can't express
    @classmethod
    def create(cls, application: WSGIApplication, **kwargs: _WSGIServerParams) -> Self: ...
    def wait(self, retries: int = 30) -> bool: ...
