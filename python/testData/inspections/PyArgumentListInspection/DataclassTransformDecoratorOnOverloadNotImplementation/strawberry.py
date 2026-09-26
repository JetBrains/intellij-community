from typing import overload, dataclass_transform, Sequence, Callable


def field():
    pass


class StrawberryField:
    pass


@overload
@dataclass_transform(
    order_default=True, kw_only_default=True, field_specifiers=(field, StrawberryField)
)
def type[T](
        cls: T,
        *,
        name: str | None = None,
        is_input: bool = False,
        is_interface: bool = False,
        description: str | None = None,
        directives: Sequence[object] | None = (),
        extend: bool = False,
) -> T: ...


@overload
@dataclass_transform(
    order_default=True, kw_only_default=True, field_specifiers=(field, StrawberryField)
)
def type[T](
        *,
        name: str | None = None,
        is_input: bool = False,
        is_interface: bool = False,
        description: str | None = None,
        directives: Sequence[object] | None = (),
        extend: bool = False,
) -> Callable[[T], T]: ...


def type[T](
        cls: T | None = None,
        *,
        name: str | None = None,
        is_input: bool = False,
        is_interface: bool = False,
        description: str | None = None,
        directives: Sequence[object] | None = (),
        extend: bool = False,
) -> T | Callable[[T], T]:
    pass
