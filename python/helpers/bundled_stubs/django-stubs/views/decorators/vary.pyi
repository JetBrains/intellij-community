from collections.abc import Callable
from typing import Any, TypeVar

_F = TypeVar("_F", bound=Callable[..., Any])

def vary_on_headers(*headers: str) -> Callable[[_F], _F]: ...
def vary_on_cookie(func: _F) -> _F: ...
