from collections.abc import Callable
from typing import Any, overload

from typing_extensions import TypeVar

_C = TypeVar("_C", bound=Callable[..., Any])

@overload
def async_unsafe(message: str) -> Callable[[_C], _C]: ...
@overload
def async_unsafe(message: _C) -> _C: ...
