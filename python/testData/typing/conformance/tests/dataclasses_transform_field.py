"""
Tests the dataclass_transform mechanism honors implicit default values
in field parameters.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/dataclasses.html#field-specifier-parameters

from typing import Any, Callable, Literal, TypeVar, dataclass_transform, overload

T = TypeVar("T")

# > Field specifier functions can use overloads that implicitly specify the
# > value of init using a literal bool value type (Literal[False] or Literal[True]).

@overload
def field1(
    *,
    default: str | None = None,
    resolver: Callable[[], Any],
    init: Literal[False] = False,
) -> Any:
    ...


@overload
def field1(
    *,
    default: str | None = None,
    resolver: None = None,
    init: Literal[True] = True,
) -> Any:
    ...


def field1(
    *,
    default: str | None = None,
    resolver: Callable[[], Any] | None = None,
    init: bool = True,
) -> Any:
    ...


def field2(*, init: bool = False, kw_only: bool = True) -> Any:
    ...


@dataclass_transform(kw_only_default=True, field_specifiers=(field1, field2))
def create_model(*, init: bool = True) -> Callable[[type[T]], type[T]]:
    ...


@create_model()
class CustomerModel1:
    id: int = field1(resolver=lambda: 0)
    name: str = field1(default="Voldemort")


CustomerModel1()
CustomerModel1(name="hi")

# This should generate an error because "id" is not
# supposed to be part of the init function.
CustomerModel1(id=1, name="hi")  # E


@create_model()
class CustomerModel2:
    id: int = field2()
    name: str = field2(init=True)


# This should generate an error because kw_only is True
# by default for field2.
CustomerModel2(1)  # E

CustomerModel2(name="Fred")
