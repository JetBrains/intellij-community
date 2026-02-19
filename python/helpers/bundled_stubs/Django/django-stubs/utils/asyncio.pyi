from collections.abc import Callable
from typing import TypeVar, overload

_C = TypeVar("_C", bound=Callable)

@overload
def async_unsafe(message: str) -> Callable[[_C], _C]: ...
@overload
def async_unsafe(message: _C) -> _C: ...
