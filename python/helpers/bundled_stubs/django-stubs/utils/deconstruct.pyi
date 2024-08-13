from collections.abc import Callable, Sequence
from typing import Any, TypeVar, overload, type_check_only

from typing_extensions import Self

# Contains additions from a class being decorated with '@deconstructible'
@type_check_only
class _Deconstructible:
    def __new__(cls, *args: Any, **kwargs: Any) -> Self: ...
    def deconstruct(obj) -> tuple[str, Sequence[Any], dict[str, Any]]: ...

_T = TypeVar("_T")
_TCallable = TypeVar("_TCallable", bound=Callable[..., Any])

@overload
def deconstructible(_type: type[_T]) -> type[_T]: ...
@overload
def deconstructible(*, path: str | None = ...) -> Callable[[_TCallable], _TCallable]: ...
