from typing import Any

class Binding:
    ffi: Any | None
    lib: Any | None
    def init_static_locks(self) -> None: ...
