"""
Tests subtyping rules for callables.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/callables.html#subtyping-rules-for-callables

from typing import Callable, ParamSpec, Protocol, TypeAlias, overload

P = ParamSpec("P")

# > Callable types are covariant with respect to their return types but
# > contravariant with respect to their parameter types.


def func1(
    cb1: Callable[[float], int],
    cb2: Callable[[float], float],
    cb3: Callable[[int], int],
) -> None:
    f1: Callable[[int], float] = cb1  # OK
    f2: Callable[[int], float] = cb2  # OK
    f3: Callable[[int], float] = cb3  # OK

    f4: Callable[[float], float] = cb1  # OK
    f5: Callable[[float], float] = cb2  # OK
    f6: Callable[[float], float] = cb3  # E

    f7: Callable[[int], int] = cb1  # OK
    f8: Callable[[int], int] = cb2  # E
    f9: Callable[[int], int] = cb3  # OK


# > Callable A is a subtype of callable B if all keyword-only parameters in B
# > are present in A as either keyword-only parameters or standard (positional
# > or keyword) parameters.


class PosOnly2(Protocol):
    def __call__(self, b: int, a: int, /) -> None: ...


class KwOnly2(Protocol):
    def __call__(self, *, b: int, a: int) -> None: ...


class Standard2(Protocol):
    def __call__(self, a: int, b: int) -> None: ...


def func2(standard: Standard2, pos_only: PosOnly2, kw_only: KwOnly2):
    f1: Standard2 = pos_only  # E
    f2: Standard2 = kw_only  # E

    f3: PosOnly2 = standard  # OK
    f4: PosOnly2 = kw_only  # E

    f5: KwOnly2 = standard  # OK
    f6: KwOnly2 = pos_only  # E


# > If a callable B has a signature with a *args parameter, callable A
# > must also have a *args parameter to be a subtype of B, and the type of
# > B’s *args parameter must be a subtype of A’s *args parameter.


class NoArgs3(Protocol):
    def __call__(self) -> None: ...


class IntArgs3(Protocol):
    def __call__(self, *args: int) -> None: ...


class FloatArgs3(Protocol):
    def __call__(self, *args: float) -> None: ...


def func3(no_args: NoArgs3, int_args: IntArgs3, float_args: FloatArgs3):
    f1: NoArgs3 = int_args  # OK
    f2: NoArgs3 = float_args  # OK

    f3: IntArgs3 = no_args  # E: missing *args parameter
    f4: IntArgs3 = float_args  # OK

    f5: FloatArgs3 = no_args  # E: missing *args parameter
    f6: FloatArgs3 = int_args  # E: float is not subtype of int


# > If a callable B has a signature with one or more positional-only parameters,
# > a callable A is a subtype of B if A has an *args parameter whose type is a
# > supertype of the types of any otherwise-unmatched positional-only parameters
# > in B.


class PosOnly4(Protocol):
    def __call__(self, a: int, b: str, /) -> None: ...


class IntArgs4(Protocol):
    def __call__(self, *args: int) -> None: ...


class IntStrArgs4(Protocol):
    def __call__(self, *args: int | str) -> None: ...


class StrArgs4(Protocol):
    def __call__(self, a: int, /, *args: str) -> None: ...


class Standard4(Protocol):
    def __call__(self, a: int, b: str) -> None: ...


def func4(int_args: IntArgs4, int_str_args: IntStrArgs4, str_args: StrArgs4):
    f1: PosOnly4 = int_args  # E: str is not subtype of int
    f2: PosOnly4 = int_str_args  # OK
    f3: PosOnly4 = str_args  # OK
    f4: IntStrArgs4 = str_args  # E: int | str is not subtype of str
    f5: IntStrArgs4 = int_args  # E: int | str is not subtype of int
    f6: StrArgs4 = int_str_args  # OK
    f7: StrArgs4 = int_args  # E: str is not subtype of int
    f8: IntArgs4 = int_str_args  # OK
    f9: IntArgs4 = str_args  # E: int is not subtype of str
    f10: Standard4 = int_str_args  # E: keyword parameters a and b missing
    f11: Standard4 = str_args  # E: keyword parameter b missing


# > If a callable B has a signature with a **kwargs parameter (without an
# > unpacked TypedDict type annotation), callable A must also have a **kwargs
# > parameter to be a subtype of B, and the type of B’s **kwargs parameter
# > must be a subtype of A’s **kwargs parameter.


class NoKwargs5(Protocol):
    def __call__(self) -> None: ...


class IntKwargs5(Protocol):
    def __call__(self, **kwargs: int) -> None: ...


class FloatKwargs5(Protocol):
    def __call__(self, **kwargs: float) -> None: ...


def func5(no_kwargs: NoKwargs5, int_kwargs: IntKwargs5, float_kwargs: FloatKwargs5):
    f1: NoKwargs5 = int_kwargs  # OK
    f2: NoKwargs5 = float_kwargs  # OK

    f3: IntKwargs5 = no_kwargs  # E: missing **kwargs parameter
    f4: IntKwargs5 = float_kwargs  # OK

    f5: FloatKwargs5 = no_kwargs  # E: missing **kwargs parameter
    f6: FloatKwargs5 = int_kwargs  # E: float is not subtype of int


# > If a callable B has a signature with one or more keyword-only parameters,
# > a callable A is a subtype of B if A has a **kwargs parameter whose type
# > is a supertype of the types of any otherwise-unmatched keyword-only
# > parameters in B.


class KwOnly6(Protocol):
    def __call__(self, *, a: int, b: str) -> None: ...


class IntKwargs6(Protocol):
    def __call__(self, **kwargs: int) -> None: ...


class IntStrKwargs6(Protocol):
    def __call__(self, **kwargs: int | str) -> None: ...


class StrKwargs6(Protocol):
    def __call__(self, *, a: int, **kwargs: str) -> None: ...


class Standard6(Protocol):
    def __call__(self, a: int, b: str) -> None: ...


def func6(
    int_kwargs: IntKwargs6, int_str_kwargs: IntStrKwargs6, str_kwargs: StrKwargs6
):
    f1: KwOnly6 = int_kwargs  # E: str is not subtype of int
    f2: KwOnly6 = int_str_kwargs  # OK
    f3: KwOnly6 = str_kwargs  # OK
    f4: IntStrKwargs6 = str_kwargs  # E: int | str is not subtype of str
    f5: IntStrKwargs6 = int_kwargs  # E: int | str is not subtype of int
    f6: StrKwargs6 = int_str_kwargs  # OK
    f7: StrKwargs6 = int_kwargs  # E: str is not subtype of int
    f8: IntKwargs6 = int_str_kwargs  # OK
    f9: IntKwargs6 = str_kwargs  # E: int is not subtype of str
    f10: Standard6 = int_str_kwargs  # E: Does not accept positional arguments
    f11: Standard6 = str_kwargs  # E: Does not accept positional arguments


# > A signature that includes *args: P.args, **kwargs: P.kwargs is equivalent
# > to a Callable parameterized by P.


class ProtocolWithP(Protocol[P]):
    def __call__(self, *args: P.args, **kwargs: P.kwargs) -> None: ...


TypeAliasWithP: TypeAlias = Callable[P, None]


def func7(proto: ProtocolWithP[P], ta: TypeAliasWithP[P]):
    # These two types are equivalent
    f1: TypeAliasWithP[P] = proto  # OK
    f2: ProtocolWithP[P] = ta  # OK


# > If a callable A has a parameter x with a default argument value and B is
# > the same as A except that x has no default argument, then A is a subtype
# > of B. A is also a subtype of C if C is the same as A with parameter x
# > removed.


class DefaultArg8(Protocol):
    def __call__(self, x: int = 0) -> None: ...


class NoDefaultArg8(Protocol):
    def __call__(self, x: int) -> None: ...


class NoX8(Protocol):
    def __call__(self) -> None: ...


def func8(default_arg: DefaultArg8, no_default_arg: NoDefaultArg8, no_x: NoX8):
    f1: DefaultArg8 = no_default_arg  # E
    f2: DefaultArg8 = no_x  # E

    f3: NoDefaultArg8 = default_arg  # OK
    f4: NoDefaultArg8 = no_x  # E

    f5: NoX8 = default_arg  # OK
    f6: NoX8 = no_default_arg  # E


# > If a callable A is overloaded with two or more signatures, it is a subtype
# > of callable B if at least one of the overloaded signatures in A is a
# > subtype of B.


class Overloaded9(Protocol):
    @overload
    def __call__(self, x: int) -> int: ...
    @overload
    def __call__(self, x: str) -> str: ...


class IntArg9(Protocol):
    def __call__(self, x: int) -> int: ...


class StrArg9(Protocol):
    def __call__(self, x: str) -> str: ...


class FloatArg9(Protocol):
    def __call__(self, x: float) -> float: ...


def func9(overloaded: Overloaded9):
    f1: IntArg9 = overloaded  # OK
    f2: StrArg9 = overloaded  # OK
    f3: FloatArg9 = overloaded  # E


# > If a callable B is overloaded with two or more signatures, callable A is
# > a subtype of B if A is a subtype of all of the signatures in B.


class Overloaded10(Protocol):
    @overload
    def __call__(self, x: int, y: str) -> float: ...
    @overload
    def __call__(self, x: str) -> complex: ...


class StrArg10(Protocol):
    def __call__(self, x: str) -> complex: ...


class IntStrArg10(Protocol):
    def __call__(self, x: int | str, y: str = "") -> int: ...


def func10(int_str_arg: IntStrArg10, str_arg: StrArg10):
    f1: Overloaded10 = int_str_arg  # OK
    f2: Overloaded10 = str_arg  # E
