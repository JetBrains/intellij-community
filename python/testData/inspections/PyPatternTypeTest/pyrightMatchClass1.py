# This sample tests type checking for match statements (as
# described in PEP 634) that contain class patterns.

from typing import (
    Any,
    Generic,
    Literal,
    NamedTuple,
    Protocol,
    TypeVar,
    TypedDict,
    runtime_checkable,
    assert_type,
    Never
)
from typing_extensions import (  # pyright: ignore[reportMissingModuleSource]
    LiteralString,
)
from dataclasses import dataclass, field

foo = 3

T = TypeVar("T")


class ClassA:
    __match_args__ = ("attr_a", "attr_b")
    attr_a: int
    attr_b: str


class ClassB(Generic[T]):
    __match_args__ = ("attr_a", "attr_b")
    attr_a: T
    attr_b: str


class ClassC: ...


class ClassD(ClassC): ...


def test_unknown(value_to_match):
    match value_to_match:
        case ClassA(attr_a=a2) as a1:
            assert_type(a1, ClassA)
            assert_type(a2, int)
            assert_type(value_to_match, ClassA)

        # This should generate an error because foo isn't instantiable.
        case foo() as a3:
            pass


def test_any(value_to_match: Any):
    match value_to_match:
        case list() as a1:
            assert_type(a1, list)
            assert_type(value_to_match, list)


def test_custom_type(value_to_match: ClassA | ClassB[int] | ClassB[str] | ClassC):
    match value_to_match:
        case int() as a1:
            assert_type(a1, int)
            assert_type(value_to_match, int)

        case ClassA(attr_a=a4, attr_b=a5) as a3:
            assert_type(a3, ClassA)
            assert_type(a4, int)
            assert_type(a5, str)
            assert_type(value_to_match, ClassA)
            assert_type(value_to_match, ClassA)

        case ClassB(a6, a7):
            assert_type(a6, int | str)
            assert_type(a7, str)
            assert_type(value_to_match, ClassB[int] | ClassB[str])

        case ClassD() as a2:
            assert_type(a2, ClassD)
            assert_type(value_to_match, ClassD)

        case ClassC() as a8:
            assert_type(a8, ClassC)
            assert_type(value_to_match, ClassC)


def test_subclass(value_to_match: ClassD):
    match value_to_match:
        case ClassC() as a1:
            assert_type(a1, ClassD)

        case _ as a2:
            assert_type(a2, Never)


def test_literal(value_to_match: Literal[3]):
    match value_to_match:
        case int() as a1:
            assert_type(a1, Literal[3])
            assert_type(value_to_match, Literal[3])

        case float() as a2:
            assert_type(a2, Never)
            assert_type(value_to_match, Never)

        case str() as a3:
            assert_type(a3, Never)
            assert_type(value_to_match, Never)


def test_literal_string(value_to_match: LiteralString) -> None:
    match value_to_match:
        case "a" as a1:
            assert_type(value_to_match, Literal['a'])
            assert_type(a1, Literal['a'])
        case str() as a2:
            assert_type(value_to_match, LiteralString)
            assert_type(a2, LiteralString)
        case a3:
            assert_type(value_to_match, Never)
            assert_type(a3, Never)


TFloat = TypeVar("TFloat", bound=float)


def test_bound_typevar(value_to_match: TFloat) -> TFloat:
    match value_to_match:
        case int() as a1:
            assert_type(a1, int)
            assert_type(value_to_match, int)

        case float() as a2:
            assert_type(a2, float)
            assert_type(value_to_match, float)

        case str() as a3:
            assert_type(a3, str)
            assert_type(value_to_match, str)

    return value_to_match


# TInt = TypeVar("TInt", bound=int)


# def test_union(
#         value_to_match: TInt | Literal[3] | float | str,
# ) -> TInt | Literal[3] | float | str:
#     match value_to_match:
#         case int() as a1:
#             assert_type(a1, int)
#             assert_type(value_to_match, int)
# 
#         case float() as a2:
#             assert_type(a2, float)
#             assert_type(value_to_match, float)
# 
#         case str() as a3:
#             assert_type(a3, str)
#             assert_type(value_to_match, str)
# 
#     return value_to_match


T = TypeVar("T")


class Point(Generic[T]):
    __match_args__ = ("x", "y")
    x: T
    y: T


def func1(points: list[Point[float] | Point[complex]]):
    match points:
        case [] as a1:
            assert_type(a1, list[Point[float] | Point[complex]])
            assert_type(points, list[Point[float] | Point[complex]])

        case [Point(0, 0) as b1]:
            assert_type(b1, Point[float] | Point[complex])
            assert_type(points, list[Point[float] | Point[complex]])

        case [Point(c1, c2)]:
            assert_type(c1, float | complex)
            assert_type(c2, float | complex)
            assert_type(points, list[Point[float] | Point[complex]])

        case [Point(0, d1), Point(0, d2)]:
            assert_type(d1, float | complex)
            assert_type(d2, float | complex)
            assert_type(points, list[Point[float] | Point[complex]])

        case _ as e1:
            assert_type(e1, list[Point[float] | Point[complex]])
            assert_type(points, list[Point[float] | Point[complex]])


def func2(subj: object):
    match subj:
        case list() as a1:
            assert_type(a1, list)
            assert_type(subj, list)


def func3(subj: int | str | dict[str, str]):
    match subj:
        case int(x):
            assert_type(x, int)
            assert_type(subj, int)

        case str(x):
            assert_type(x, str)
            assert_type(subj, str)

        case dict(x):
            assert_type(x, dict[str, str])
            assert_type(subj, dict[str, str])


def func4(subj: object):
    match subj:
        case int(x):
            assert_type(x, int)
            assert_type(subj, int)

        case str(x):
            assert_type(x, str)
            assert_type(subj, str)


# Test the auto-generation of __match_args__ for dataclass.
@dataclass
class Dataclass1:
    val1: int
    val2: str = field(init=False)
    val3: complex


@dataclass
class Dataclass2:
    val1: int
    val2: str
    val3: float


def func5(subj: object):
    match subj:
        case Dataclass1(a, b):
            assert_type(a, int)
            assert_type(b, complex)
            assert_type(subj, Dataclass1)

        case Dataclass2(a, b, c):
            assert_type(a, int)
            assert_type(b, str)
            assert_type(c, float)
            assert_type(subj, Dataclass2)


# # Test the auto-generation of __match_args__ for named tuples.
# NT1 = NamedTuple("NT1", [("val1", int), ("val2", complex)])
# NT2 = NamedTuple("NT2", [("val1", int), ("val2", str), ("val3", float)])
# 
# 
# def func6(subj: object):
#     match subj:
#         case NT1(a, b):
#             assert_type(a, int)
#             assert_type(b, complex)
#             assert_type(subj, NT1)
# 
#         case NT2(a, b, c):
#             assert_type(a, int)
#             assert_type(b, str)
#             assert_type(c, float)
#             assert_type(subj, NT2)
# 
# 
# def func7(subj: object):
#     match subj:
#         case complex(real=a, imag=b):
#             assert_type(a, float)
#             assert_type(b, float)


# T2 = TypeVar("T2")
# 
# 
# class Parent(Generic[T]): ...
# 
# 
# class Child1(Parent[T]): ...
# 
# 
# class Child2(Parent[T], Generic[T, T2]): ...
# 
# 
# def func8(subj: Parent[int]):
#     match subj:
#         case Child1() as a1:
#             assert_type(a1, Child1[int])
#             assert_type(subj, Child1[int])
# 
#         case Child2() as b1:
#             assert_type(b1, Child2[int, Unknown])
#             assert_type(subj, Child2[int, Unknown])


T3 = TypeVar("T3")


def func9(v: T3) -> T3 | None:
    match v:
        case str():
            assert_type(v, str)
            return v

        case _:
            return None


T4 = TypeVar("T4", int, str)


def func10(v: T4) -> T4 | None:
    match v:
        case str():
            assert_type(v, str)
            return v

        case int():
            assert_type(v, int)
            return v

        case list():
            assert_type(v, list)
            return v

        case _:
            return None


def func11(subj: Any):
    match subj:
        case Child1() as a1:
            assert_type(a1, Child1)
            assert_type(subj, Child1)

        case Child2() as b1:
            assert_type(b1, Child2)
            assert_type(subj, Child2)


class TD1(TypedDict):
    x: int


def func12(subj: int, flt_cls: type[float], union_val: float | int):
    match subj:
        # This should generate an error because int doesn't accept two arguments.
        case int(1, 2):
            pass

    match subj:
        # This should generate an error because float doesn't accept keyword arguments.
        case float(x=1):
            pass

    match subj:
        case flt_cls():
            pass

        # This should generate an error because it is a union.
        case union_val():
            pass

        # This should generate an error because it is a TypedDict.
        case TD1():
            pass


# def func13(subj: tuple[Literal[0]]):
#     match subj:
#         case tuple((1,)) as a:
#             assert_type(subj, Never)
#             assert_type(a, Never)
# 
#         case tuple((0, 0)) as b:
#             assert_type(subj, Never)
#             assert_type(b, Never)
# 
#         case tuple((0,)) as c:
#             assert_type(subj, tuple[Literal[0]])
#             assert_type(c, tuple[Literal[0]])
# 
#         case d:
#             assert_type(subj, Never)
#             assert_type(d, Never)


# class ClassE(Generic[T]):
#     __match_args__ = ("x",)
#     x: list[T]
# 
# 
# class ClassF(ClassE[T]):
#     pass
# 
# 
# def func14(subj: ClassE[T]) -> T | None:
#     match subj:
#         case ClassF(a):
#             assert_type(subj, ClassF[T])
#             assert_type(a, list[T])
#             return a[0]
# 
# 
# class IntPair(tuple[int, int]):
#     pass
# 
# 
# def func15(x: IntPair | None) -> None:
#     match x:
#         case IntPair((y, z)):
#             assert_type(y, int)
#             assert_type(z, int)
# 
# 
# def func16(x: str | float | bool | None):
#     match x:
#         case str(v) | bool(v) | float(v):
#             assert_type(v, str | bool | float)
#             assert_type(x, str | bool | float)
#         case v:
#             assert_type(v, int | None)
#             assert_type(x, int | None)
#     assert_type(x, str | bool | float | int | None)
# 
# 
# def func17(x: str | float | bool | None):
#     match x:
#         case str() | float() | bool():
#             assert_type(x, str | float | bool)
#         case _:
#             assert_type(x, int | None)
#     assert_type(x, str | float | bool | int | None)
# 
# 
# def func18(x: str | float | bool | None):
#     match x:
#         case str(v) | float(v) | bool(v):
#             assert_type(v, str | float | bool)
#             assert_type(x, str | float | bool)
#         case _:
#             assert_type(x, int | None)
#     assert_type(x, str | float | bool | int | None)
# 
# 
# T5 = TypeVar("T5", complex, str)
# 
# 
# def func19(x: T5) -> T5:
#     match x:
#         case complex():
#             return x
#         case str():
#             return x
# 
#     assert_type(x, float | int)
#     return x
# 
# 
# T6 = TypeVar("T6", bound=complex | str)
# 
# 
# def func20(x: T6) -> T6:
#     match x:
#         case complex():
#             return x
#         case str():
#             return x
# 
#     assert_type(x, float | int)
#     return x


@runtime_checkable
class Proto1(Protocol):
    x: int


class Proto2(Protocol):
    x: int


def func21(subj: object):
    match subj:
        case Proto1():
            pass

        # This should generate an error because Proto2 isn't runtime checkable.
        case Proto2():
            pass


class Impl1:
    x: int


def func22(subj: Proto1 | int):
    match subj:
        case Proto1():
            assert_type(subj, Proto1)

        case _:
            assert_type(subj, int)