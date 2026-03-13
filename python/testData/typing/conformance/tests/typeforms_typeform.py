"""
Tests TypeForm functionality.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/type-forms.html#typeform

import types
from typing import Annotated, Any, ClassVar, Final, Literal, Optional, Self, TypeVarTuple, Unpack, assert_type
from typing_extensions import TypeForm


# > ``TypeForm[T]`` describes the set of all type form objects that represent
# > the type ``T`` or types that are assignable to ``T``.

ok1: TypeForm[str | None] = str | None  # OK
ok2: TypeForm[str | None] = str  # OK
ok3: TypeForm[str | None] = None  # OK
ok4: TypeForm[str | None] = Literal[None]  # OK
ok5: TypeForm[str | None] = Optional[str]  # OK
ok6: TypeForm[str | None] = "str | None"  # OK
ok7: TypeForm[str | None] = Any  # OK

err1: TypeForm[str | None] = str | int  # E
err2: TypeForm[str | None] = list[str | None]  # E


# > ``TypeForm[Any]`` is assignable both to and from any other ``TypeForm`` type.

v_any: TypeForm[Any] = int | str
v_str: TypeForm[str] = str
assert_type(v_any, TypeForm[Any])
assert_type(v_str, TypeForm[str])

v_any = v_str  # OK
v_str = v_any  # OK

# > The type expression ``TypeForm``, with no type argument provided, is
# > equivalent to ``TypeForm[Any]``.

def func1(x: TypeForm) -> None:
    assert_type(x, TypeForm[Any])


# > Valid type expressions are assignable to ``TypeForm`` through implicit TypeForm evaluation.

v1_actual: types.UnionType = str | None  # OK
v1_type_form: TypeForm[str | None] = str | None  # OK

v2_actual: types.GenericAlias = list[int]  # OK
v2_type_form: TypeForm = list[int]  # OK

v3: TypeForm[int | str] = Annotated[int | str, "metadata"]  # OK
v4: TypeForm[set[str]] = "set[str]"  # OK

func1(v3)  # OK
func1(v4)  # OK
func1(int)  # OK
func1("int")  # OK
func1("not a type")  # E


# > Expressions that are not valid type expressions should not evaluate to a ``TypeForm`` type.

Ts = TypeVarTuple("Ts")
var = 1

bad1: TypeForm = tuple()  # E
bad2: TypeForm = (1, 2)  # E
bad3: TypeForm = 1  # E
bad4: TypeForm = Self  # E
bad5: TypeForm = ClassVar[int]  # E
bad6: TypeForm = Final[int]  # E
bad7: TypeForm = Unpack[Ts]  # E
bad8: TypeForm = Optional  # E
bad9: TypeForm = "int + str"  # E


# > ``TypeForm`` acts as a function that can be called with a single valid type expression.

x1 = TypeForm(str | None)
assert_type(x1, TypeForm[str | None])

x2 = TypeForm("list[int]")
assert_type(x2, TypeForm[list[int]])

x3 = TypeForm("type(1)")  # E
x4 = type(1)  # OK
x5 = TypeForm(type(1))  # E


# > ``TypeForm`` is covariant in its single type parameter.

def get_type_form() -> TypeForm[int]:
    return int


t1: TypeForm[int | str] = get_type_form()  # OK
t2: TypeForm[str] = get_type_form()  # E


# > ``type[T]`` is a subtype of ``TypeForm[T]``.

def get_type() -> type[int]:
    return int


t3: TypeForm[int | str] = get_type()  # OK
t4: TypeForm[str] = get_type()  # E
