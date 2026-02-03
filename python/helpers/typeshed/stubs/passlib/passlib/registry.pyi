from typing import Any

class _PasslibRegistryProxy:
    __name__: str
    __package__: str | None
    def __getattr__(self, attr: str) -> Any: ...  # returns handler that is a value from object.__dict__
    def __setattr__(self, attr: str, value) -> None: ...
    def __dir__(self) -> list[str]: ...

def register_crypt_handler_path(name: str, path: str) -> None: ...
def register_crypt_handler(
    handler: Any, force: bool = False, _attr: str | None = None
) -> None: ...  # expected handler is object with attr handler.name
def get_crypt_handler(name: str, default: Any = ...) -> Any: ...  # returns handler or default
def list_crypt_handlers(loaded_only: bool = False) -> list[str]: ...

__all__ = ["register_crypt_handler_path", "register_crypt_handler", "get_crypt_handler", "list_crypt_handlers"]
