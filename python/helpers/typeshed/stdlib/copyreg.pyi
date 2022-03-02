from typing import Any, Callable, Hashable, Optional, SupportsInt, TypeVar, Union

_TypeT = TypeVar("_TypeT", bound=type)
_Reduce = Union[tuple[Callable[..., _TypeT], tuple[Any, ...]], tuple[Callable[..., _TypeT], tuple[Any, ...], Optional[Any]]]

__all__ = ["pickle", "constructor", "add_extension", "remove_extension", "clear_extension_cache"]

def pickle(
    ob_type: _TypeT,
    pickle_function: Callable[[_TypeT], str | _Reduce[_TypeT]],
    constructor_ob: Callable[[_Reduce[_TypeT]], _TypeT] | None = ...,
) -> None: ...
def constructor(object: Callable[[_Reduce[_TypeT]], _TypeT]) -> None: ...
def add_extension(module: Hashable, name: Hashable, code: SupportsInt) -> None: ...
def remove_extension(module: Hashable, name: Hashable, code: int) -> None: ...
def clear_extension_cache() -> None: ...

dispatch_table: dict[type, Callable[[type], str | _Reduce[type]]]  # undocumented
