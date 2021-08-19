from typing import Any, AnyStr, Callable, Sequence, Type, TypeVar

def with_repr(attrs: Sequence[AnyStr | Attribute]) -> Callable[..., Any]: ...
def with_cmp(attrs: Sequence[AnyStr | Attribute]) -> Callable[..., Any]: ...
def with_init(attrs: Sequence[AnyStr | Attribute]) -> Callable[..., Any]: ...
def immutable(attrs: Sequence[AnyStr | Attribute]) -> Callable[..., Any]: ...
def strip_leading_underscores(attribute_name: AnyStr) -> AnyStr: ...

NOTHING = Any

_T = TypeVar("_T")

def attributes(
    attrs: Sequence[AnyStr | Attribute],
    apply_with_cmp: bool = ...,
    apply_with_init: bool = ...,
    apply_with_repr: bool = ...,
    apply_immutable: bool = ...,
    store_attributes: Callable[[type, Attribute], Any] | None = ...,
    **kw: dict[Any, Any] | None,
) -> Callable[[Type[_T]], Type[_T]]: ...

class Attribute:
    def __init__(
        self,
        name: AnyStr,
        exclude_from_cmp: bool = ...,
        exclude_from_init: bool = ...,
        exclude_from_repr: bool = ...,
        exclude_from_immutable: bool = ...,
        default_value: Any = ...,
        default_factory: Callable[[None], Any] | None = ...,
        instance_of: Any | None = ...,
        init_aliaser: Callable[[AnyStr], AnyStr] | None = ...,
    ) -> None: ...
