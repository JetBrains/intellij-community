from _typeshed import Incomplete
from types import ModuleType
from typing import Protocol

# Protocol for flask.Flask class
class _Flask(Protocol):
    def before_request(self, f): ...
    def after_request(self, f): ...
    def teardown_request(self, f): ...

flask_lib: ModuleType
request: Incomplete

class Pony:
    app: _Flask | None
    def __init__(self, app: _Flask | None = None) -> None: ...
    def init_app(self, app: _Flask) -> None: ...
