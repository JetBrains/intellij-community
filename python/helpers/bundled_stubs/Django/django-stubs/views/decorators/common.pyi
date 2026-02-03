from collections.abc import Callable
from typing import Any, TypeVar

_C = TypeVar("_C", bound=Callable[..., Any])

def no_append_slash(view_func: _C) -> _C: ...
