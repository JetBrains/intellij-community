"""
Tests handling of the LiteralString special form.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/literal.html#literalstring


from typing import (
    Any,
    Generic,
    Literal,
    LiteralString,
    Sequence,
    TypeVar,
    assert_type,
    overload,
)


variable_annotation: LiteralString


def my_function(literal_string: LiteralString) -> LiteralString:
    ...


class Foo:
    my_attribute: LiteralString = ""


type_argument: list[LiteralString]

T = TypeVar("T", bound=LiteralString)


bad_union: Literal["hello", LiteralString]  # E
bad_nesting: Literal[LiteralString]  # E


def func1(a: Literal["one"], b: Literal["two"]):
    x1: LiteralString = a

    x2: Literal[""] = b  # E


def func2(a: LiteralString, b: LiteralString):
    # > Addition: x + y is of type LiteralString if both x and y are compatible with LiteralString.
    assert_type(a + b, LiteralString)

    # > Joining: sep.join(xs) is of type LiteralString if sep’s type is
    # > compatible with LiteralString and xs’s type is compatible with Iterable[LiteralString].
    assert_type(",".join((a, b)), LiteralString)
    assert_type(",".join((a, str(b))), str)

    # > In-place addition: If s has type LiteralString and x has type compatible with
    # > LiteralString, then s += x preserves s’s type as LiteralString.
    a += "hello"
    b += a

    # > String formatting: An f-string has type LiteralString if and only if its constituent
    # > expressions are literal strings. s.format(...) has type LiteralString if and only if
    # > s and the arguments have types compatible with LiteralString.
    assert_type(f"{a} {b}", LiteralString)

    variable = 3
    x1: LiteralString = f"{a} {str(variable)}"  # E

    assert_type(a + str(1), str)

    # > LiteralString is compatible with the type str
    x2: str = a

    # > Other literal types, such as literal integers, are not compatible with LiteralString.
    x3: LiteralString = 3  # E
    x4: LiteralString = b"test"  # E


# > Conditional statements and expressions work as expected.
def condition1() -> bool:
    ...


def return_literal_string() -> LiteralString:
    return "foo" if condition1() else "bar"  # OK


def return_literal_str2(literal_string: LiteralString) -> LiteralString:
    return "foo" if condition1() else literal_string  # OK


def return_literal_str3() -> LiteralString:
    result: LiteralString
    if condition1():
        result = "foo"
    else:
        result = "bar"

    return result  # OK


# > TypeVars can be bound to LiteralString.
TLiteral = TypeVar("TLiteral", bound=LiteralString)


def literal_identity(s: TLiteral) -> TLiteral:
    return s


def func3(s: Literal["hello"]):
    y = literal_identity(s)
    assert_type(y, Literal["hello"])


def func4(s: LiteralString):
    y2 = literal_identity(s)
    assert_type(y2, LiteralString)


def func5(s: str):
    literal_identity(s)  # E


# > LiteralString can be used as a type argument for generic classes.
class Container(Generic[T]):
    def __init__(self, value: T) -> None:
        self.value = value


def func6(s: LiteralString):
    x: Container[LiteralString] = Container(s)  # OK


def func7(s: str):
    x_error: Container[LiteralString] = Container(s)  # E


# > Standard containers like List work as expected.
xs: list[LiteralString] = ["foo", "bar", "baz"]


class A: pass
class B(A): pass
class C(B): pass


@overload
def func8(x: Literal["foo"]) -> C:
    ...


@overload
def func8(x: LiteralString) -> B:
    ...


@overload
def func8(x: str) -> A:
    ...


def func8(x: Any) -> Any:
    ...


assert_type(func8("foo"), C)  # First overload
assert_type(func8("bar"), B)  # Second overload
assert_type(func8(str(1)), A)  # Third overload


def func9(val: list[LiteralString]):
    x1: list[str] = val  # E


def func10(val: Sequence[LiteralString]):
    x1: Sequence[str] = val  # OK
