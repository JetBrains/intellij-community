from collections.abc import Callable
from typing import Any, TypeVar, overload
from typing_extensions import Literal, TypeAlias

_F = TypeVar("_F", bound=Callable[..., Any])
_Actions: TypeAlias = Literal["default", "error", "ignore", "always", "module", "once"]

string_types: tuple[type, ...]

class ClassicAdapter:
    reason: str
    version: str
    action: _Actions | None
    category: type[Warning]
    def __init__(
        self, reason: str = ..., version: str = ..., action: _Actions | None = ..., category: type[Warning] = ...
    ) -> None: ...
    def get_deprecated_msg(self, wrapped: Callable[..., Any], instance: object) -> str: ...
    def __call__(self, wrapped: _F) -> Callable[[_F], _F]: ...

@overload
def deprecated(__wrapped: _F) -> _F: ...
@overload
def deprecated(
    reason: str = ..., *, version: str = ..., action: _Actions | None = ..., category: type[Warning] | None = ...
) -> Callable[[_F], _F]: ...
