from typing import Any

signals_available: bool

class Namespace:
    def signal(self, name: str, doc: str | None = None) -> _FakeSignal: ...

class _FakeSignal:
    name: str
    __doc__: Any
    def __init__(self, name: str, doc: str | None = None) -> None: ...
    send: Any
    connect: Any
    disconnect: Any
    has_receivers_for: Any
    receivers_for: Any
    temporarily_connected_to: Any
    connected_to: Any

scope_changed: _FakeSignal
