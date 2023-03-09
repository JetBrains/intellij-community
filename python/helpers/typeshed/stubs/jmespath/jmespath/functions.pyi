from collections.abc import Callable
from typing import Any, TypeVar
from typing_extensions import NotRequired, TypedDict

TYPES_MAP: dict[str, str]
REVERSE_TYPES_MAP: dict[str, tuple[str, ...]]

class _Signature(TypedDict):
    types: list[str]
    variadic: NotRequired[bool]

_F = TypeVar("_F", bound=Callable[..., Any])

def signature(*arguments: _Signature) -> Callable[[_F], _F]: ...

class FunctionRegistry(type):
    def __init__(cls, name, bases, attrs) -> None: ...

class Functions:
    FUNCTION_TABLE: Any
    def call_function(self, function_name, resolved_args): ...
