from typing import Any

class _ModuleRegistry:
    module_registry: Any
    prefix: Any
    def __init__(self, prefix: str = ...) -> None: ...
    def preload_module(self, *deps): ...
    def import_prefix(self, path) -> None: ...

preloaded: Any
preload_module: Any
