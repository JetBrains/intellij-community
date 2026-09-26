"""
Tests for basic usage of default values for TypeVar-like's.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#defaults-for-type-parameters.

from typing import Any, Callable, Generic, Self, Unpack, assert_type
from typing_extensions import TypeVar, ParamSpec, TypeVarTuple


DefaultStrT = TypeVar("DefaultStrT", default=str)
DefaultIntT = TypeVar("DefaultIntT", default=int)
DefaultBoolT = TypeVar("DefaultBoolT", default=bool)
T = TypeVar("T")
T1 = TypeVar("T1")
T2 = TypeVar("T2")

# > The order for defaults should follow the standard function parameter
# > rules, so a type parameter with no ``default`` cannot follow one with
# > a ``default`` value. Doing so may raise a ``TypeError`` at runtime,
# > and a type checker should flag this as an error.


class NonDefaultFollowsDefault(Generic[DefaultStrT, T]): ...  # E: non-default TypeVars cannot follow ones with defaults


class NoNonDefaults(Generic[DefaultStrT, DefaultIntT]):
    x: DefaultStrT
    y: DefaultIntT


def test_no_non_defaults(a: NoNonDefaults, b: NoNonDefaults[str], c: NoNonDefaults[str, int]):
    assert_type(a.x, str)
    assert_type(a.y, int)

    assert_type(b.x, str)
    assert_type(b.y, int)

    assert_type(c.x, str)
    assert_type(c.y, int)


class OneDefault(Generic[T, DefaultBoolT]):
    x: T
    y: DefaultBoolT


def test_one_default(a: OneDefault[float], b: OneDefault[float, bool]):
    assert_type(a.x, float)
    assert_type(a.y, bool)

    assert_type(b.x, float)
    assert_type(b.y, bool)


class AllTheDefaults(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]):
    x1: T1
    x2: T2
    x3: DefaultStrT
    x4: DefaultIntT
    x5: DefaultBoolT


def test_all_the_defaults(
    a: AllTheDefaults,
    b: AllTheDefaults[int],  # E: expected 2 arguments to AllTheDefaults
    c: AllTheDefaults[int, complex],
    d: AllTheDefaults[int, complex, str, int, bool],
    e: AllTheDefaults[int, complex, str],
    f: AllTheDefaults[int, complex, str, int],
    g: AllTheDefaults[int, complex, str, int, bool],
):
    assert_type(a.x1, Any)
    assert_type(a.x2, Any)
    assert_type(a.x3, str)
    assert_type(a.x4, int)
    assert_type(a.x5, bool)

    assert_type(c.x1, int)
    assert_type(c.x2, complex)
    assert_type(c.x3, str)
    assert_type(c.x4, int)
    assert_type(c.x5, bool)

    assert_type(d.x1, int)
    assert_type(d.x2, complex)
    assert_type(d.x3, str)
    assert_type(d.x4, int)
    assert_type(d.x5, bool)

    assert_type(e.x1, int)
    assert_type(e.x2, complex)
    assert_type(e.x3, str)
    assert_type(e.x4, int)
    assert_type(e.x5, bool)

    assert_type(f.x1, int)
    assert_type(f.x2, complex)
    assert_type(f.x3, str)
    assert_type(f.x4, int)
    assert_type(f.x5, bool)

    assert_type(g.x1, int)
    assert_type(g.x2, complex)
    assert_type(g.x3, str)
    assert_type(g.x4, int)
    assert_type(g.x5, bool)


# > ``ParamSpec`` defaults are defined using the same syntax as
# > ``TypeVar`` \ s but use a ``list`` of types or an ellipsis
# > literal "``...``" or another in-scope ``ParamSpec``.

DefaultP = ParamSpec("DefaultP", default=[str, int])


class Class_ParamSpec(Generic[DefaultP]):
    x: Callable[DefaultP, None]


def test_param_spec_defaults(a: Class_ParamSpec):
    assert_type(a.x, Callable[[str, int], None])
    assert_type(Class_ParamSpec(), Class_ParamSpec[str, int])
    assert_type(Class_ParamSpec[[bool, bool]](), Class_ParamSpec[bool, bool])


# > ``TypeVarTuple`` defaults are defined using the same syntax as
# > ``TypeVar`` \ s, but instead of a single type, they use an unpacked tuple
# > of types or an unpacked, in-scope ``TypeVarTuple`` (see `Scoping Rules`_).

DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[str, int]])


class Class_TypeVarTuple(Generic[*DefaultTs]):
    x: tuple[*DefaultTs]


def test_type_var_tuple_defaults(a: Class_TypeVarTuple):
    assert_type(a.x, tuple[str, int])
    assert_type(Class_TypeVarTuple(), Class_TypeVarTuple[str, int])
    assert_type(Class_TypeVarTuple[int, bool](), Class_TypeVarTuple[int, bool])


AnotherDefaultTs = TypeVarTuple("AnotherDefaultTs", default=Unpack[DefaultTs])


# > If both ``bound`` and ``default`` are passed, ``default`` must be a
# > subtype of ``bound``. If not, the type checker should generate an
# > error.

Ok1 = TypeVar("Ok1", bound=float, default=int)  # OK
Invalid1 = TypeVar("Invalid1", bound=str, default=int)  # E: the bound and default are incompatible

# > For constrained ``TypeVar``\ s, the default needs to be one of the
# > constraints. A type checker should generate an error even if it is a
# > subtype of one of the constraints.

Ok2 = TypeVar("Ok2", float, str, default=float)  # OK
Invalid2 = TypeVar("Invalid2", float, str, default=int)  # E: expected one of float or str got int


# > In generic functions, type checkers may use a type parameter's default when the
# > type parameter cannot be solved to anything. We leave the semantics of this
# > usage unspecified, as ensuring the ``default`` is returned in every code path
# > where the type parameter can go unsolved may be too hard to implement. Type
# > checkers are free to either disallow this case or experiment with implementing
# > support.

T4 = TypeVar("T4", default=int)


def func1(x: int | set[T4]) -> T4:
    raise NotImplementedError


assert_type(func1(0), int)  # E[optional-default-use]
assert_type(func1(0), Any)  # E[optional-default-use]


# > A ``TypeVar`` that immediately follows a ``TypeVarTuple`` is not allowed
# > to have a default, because it would be ambiguous whether a type argument
# > should be bound to the ``TypeVarTuple`` or the defaulted ``TypeVar``.

Ts = TypeVarTuple("Ts")
T5 = TypeVar("T5", default=bool)


class Foo5(Generic[*Ts, T5]): ...  # E


# > It is allowed to have a ``ParamSpec`` with a default following a
# > ``TypeVarTuple`` with a default, as there can be no ambiguity between a
# > type argument for the ``ParamSpec`` and one for the ``TypeVarTuple``.

P = ParamSpec("P", default=[float, bool])


class Foo6(Generic[*Ts, P]):
    x: tuple[*Ts]
    y: Callable[P, None]


def test_foo6(a: Foo6[int, str], b: Foo6[int, str, [bytes]]):
    assert_type(a.x, tuple[int, str])
    assert_type(a.y, Callable[[float, bool], None])

    assert_type(b.x, tuple[int, str])
    assert_type(b.y, Callable[[bytes], None])


# > Type parameter defaults should be bound by attribute access
# > (including call and subscript).


class Foo7(Generic[DefaultIntT]):
    def meth(self, /) -> Self:
        return self

    attr: DefaultIntT


foo7 = Foo7()
assert_type(Foo7.meth(foo7), Foo7[int])
assert_type(Foo7().attr, int)
