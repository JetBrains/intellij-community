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


class NoNonDefaults(Generic[DefaultStrT, DefaultIntT]): ...


assert_type(NoNonDefaults, type[NoNonDefaults[str, int]])
assert_type(NoNonDefaults[str], type[NoNonDefaults[str, int]])
assert_type(NoNonDefaults[str, int], type[NoNonDefaults[str, int]])


class OneDefault(Generic[T, DefaultBoolT]): ...


assert_type(OneDefault[float], type[OneDefault[float, bool]])
assert_type(OneDefault[float](), OneDefault[float, bool])


class AllTheDefaults(Generic[T1, T2, DefaultStrT, DefaultIntT, DefaultBoolT]): ...


assert_type(AllTheDefaults, type[AllTheDefaults[Any, Any, str, int, bool]])
assert_type(
    AllTheDefaults[int, complex], type[AllTheDefaults[int, complex, str, int, bool]]
)

AllTheDefaults[int]  # E: expected 2 arguments to AllTheDefaults

assert_type(
    AllTheDefaults[int, complex], type[AllTheDefaults[int, complex, str, int, bool]]
)
assert_type(
    AllTheDefaults[int, complex, str],
    type[AllTheDefaults[int, complex, str, int, bool]],
)
assert_type(
    AllTheDefaults[int, complex, str, int],
    type[AllTheDefaults[int, complex, str, int, bool]],
)
assert_type(
    AllTheDefaults[int, complex, str, int, bool],
    type[AllTheDefaults[int, complex, str, int, bool]],
)


# > ``ParamSpec`` defaults are defined using the same syntax as
# > ``TypeVar`` \ s but use a ``list`` of types or an ellipsis
# > literal "``...``" or another in-scope ``ParamSpec``.

DefaultP = ParamSpec("DefaultP", default=[str, int])


class Class_ParamSpec(Generic[DefaultP]): ...


assert_type(Class_ParamSpec, type[Class_ParamSpec[str, int]])
assert_type(Class_ParamSpec(), Class_ParamSpec[str, int])
assert_type(Class_ParamSpec[[bool, bool]](), Class_ParamSpec[bool, bool])


# > ``TypeVarTuple`` defaults are defined using the same syntax as
# > ``TypeVar`` \ s but use an unpacked tuple of types instead of a single type
# > or another in-scope ``TypeVarTuple`` (see `Scoping Rules`_).

DefaultTs = TypeVarTuple("DefaultTs", default=Unpack[tuple[str, int]])


class Class_TypeVarTuple(Generic[*DefaultTs]): ...


assert_type(Class_TypeVarTuple, type[Class_TypeVarTuple[*tuple[str, int]]])
assert_type(Class_TypeVarTuple(), Class_TypeVarTuple[str, int])
assert_type(Class_TypeVarTuple[int, bool](), Class_TypeVarTuple[int, bool])


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


def func1(x: int | set[T4]) -> T4: ...


assert_type(func1(0), int)


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


class Foo6(Generic[*Ts, P]): ...  # OK


assert_type(Foo6[int, str], type[Foo6[int, str, [float, bool]]])
assert_type(Foo6[int, str, [bytes]], type[Foo6[int, str, [bytes]]])


# > Type parameter defaults should be bound by attribute access
# > (including call and subscript).


class Foo7(Generic[DefaultIntT]):
    def meth(self, /) -> Self:
        return self

    attr: DefaultIntT


assert_type(Foo7.meth, Callable[[Foo7[int]], Foo7[int]])
assert_type(Foo7.attr, int)
