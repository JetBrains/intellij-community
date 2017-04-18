from typing import Sequence, Callable, Union, Any, Optional, AnyStr, TypeVar, Type

def with_repr(attrs: Sequence[Union[AnyStr, Attribute]]) -> Callable[..., Any]: ...
def with_cmp(attrs: Sequence[Union[AnyStr, Attribute]]) -> Callable[..., Any]: ...
def with_init(attrs: Sequence[Union[AnyStr, Attribute]]) -> Callable[..., Any]: ...
def immutable(attrs: Sequence[Union[AnyStr, Attribute]]) -> Callable[..., Any]: ...

def strip_leading_underscores(attribute_name: AnyStr) -> AnyStr: ...

NOTHING = Any

_T = TypeVar('_T')

def attributes(
    attrs: Sequence[Union[AnyStr, Attribute]],
    apply_with_cmp: bool = True,
    apply_with_init: bool = True,
    apply_with_repr: bool = True,
    apply_immutable: bool = False,
    store_attributes: Optional[Callable[[type, Attribute], Any]] = None,
    **kw: Optional[dict]) -> Callable[[Type[_T]], Type[_T]]: ...

class Attribute:
    def __init__(
        self,
        name: AnyStr,
        exclude_from_cmp: bool = False,
        exclude_from_init: bool = False,
        exclude_from_repr: bool = False,
        exclude_from_immutable: bool = False,
        default_value: Any = NOTHING,
        default_factory: Optional[Callable[[None], Any]] = None,
        instance_of: Optional[Any] = None,
        init_aliaser: Optional[Callable[[AnyStr], AnyStr]] = strip_leading_underscores) -> None: ...
