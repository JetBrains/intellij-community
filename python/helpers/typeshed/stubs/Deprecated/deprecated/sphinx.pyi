from collections.abc import Callable
from typing import Any, TypeVar
from typing_extensions import Literal

from .classic import ClassicAdapter, _Actions

_F = TypeVar("_F", bound=Callable[..., Any])

class SphinxAdapter(ClassicAdapter):
    directive: Literal["versionadded", "versionchanged", "deprecated"]
    reason: str
    version: str
    action: _Actions | None
    category: type[Warning]
    def __init__(
        self,
        directive: Literal["versionadded", "versionchanged", "deprecated"],
        reason: str = "",
        version: str = "",
        action: _Actions | None = None,
        category: type[Warning] = ...,
        line_length: int = 70,
    ) -> None: ...
    def __call__(self, wrapped: _F) -> Callable[[_F], _F]: ...

def versionadded(reason: str = "", version: str = "", line_length: int = 70) -> Callable[[_F], _F]: ...
def versionchanged(reason: str = "", version: str = "", line_length: int = 70) -> Callable[[_F], _F]: ...
def deprecated(
    reason: str = "",
    version: str = "",
    line_length: int = 70,
    *,
    action: _Actions | None = ...,
    category: type[Warning] | None = ...,
) -> Callable[[_F], _F]: ...
