"""
Tests the typing.ClassVar special form.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/class-compat.html#classvar

from typing import (
    Annotated,
    Any,
    Callable,
    ClassVar,
    Final,
    Generic,
    ParamSpec,
    Protocol,
    TypeAlias,
    TypeVar,
    TypeVarTuple,
    assert_type,
    cast,
)
from typing import ClassVar as CV


var1 = 3

P = ParamSpec("P")
T = TypeVar("T")
Ts = TypeVarTuple("Ts")

# This is currently commented out because it causes mypy to crash.
# class ClassA(Generic[T, P, Unpack[Ts]]):


class ClassA(Generic[T, P]):
    # > ClassVar accepts only a single argument that should be a valid type

    bad1: ClassVar[int, str] = cast(Any, 0)  # E: too many arguments
    bad2: CV[3] = cast(Any, 0)  # E: invalid type
    bad3: CV[var] = cast(Any, 0)  # E: invalid type

    # > Note that a ClassVar parameter cannot include any type variables,
    # > regardless of the level of nesting.

    bad4: ClassVar[T] = cast(Any, 0)  # E: cannot use TypeVar
    bad5: ClassVar[list[T]] = cast(Any, 0)  # E: cannot use TypeVar
    bad6: ClassVar[Callable[P, Any]] = cast(Any, 0)  # E: cannot use ParamSpec

    # This is currently commented out because it causes mypy to crash.
    # bad7: ClassVar[tuple[*Ts]]  # E: cannot use TypeVarTuple

    bad8: ClassVar[list[str]] = {}  # E: type violation in initialization

    bad9: Final[ClassVar[int]] = 3  # E: ClassVar cannot be nested with Final
    bad10: list[ClassVar[int]] = []  # E: ClassVar cannot be nested

    good1: CV[int] = 1
    good2: ClassVar[list[str]] = []
    good3: ClassVar[Any] = 1
    # > If an assigned value is available, the type should be inferred as some type
    # > to which this value is assignable.
    # Here, type checkers could infer good4 as `float` or `Any`, for example.
    good4: ClassVar = 3.1
    # > If the `ClassVar` qualifier is used without any assigned value, the type
    # > should be inferred as `Any`:
    good5: ClassVar  #E? Type checkers may error on uninitialized ClassVar
    good6: Annotated[ClassVar[list[int]], ""] = []

    def method1(self, a: ClassVar[int]):  # E: ClassVar not allowed here
        x: ClassVar[str] = ""  # E: ClassVar not allowed here
        self.xx: ClassVar[str] = ""  # E: ClassVar not allowed here

    def method2(self) -> ClassVar[int]:  # E: ClassVar not allowed here
        return 3


bad11: ClassVar[int] = 3  # E: ClassVar not allowed here
bad12: TypeAlias = ClassVar[str]  # E: ClassVar not allowed here


assert_type(ClassA.good1, int)
assert_type(ClassA.good2, list[str])
assert_type(ClassA.good3, Any)
assert_type(ClassA.good5, Any)


class BasicStarship:
    captain: str = "Picard"  # Instance variable with default
    damage: int  # Instance variable without default
    stats: ClassVar[dict[str, int]] = {}  # Class variable

    def __init__(self, damage: int) -> None:
        self.damage = damage


class Starship:
    captain: str = "Picard"
    damage: int
    stats: ClassVar[dict[str, int]] = {}

    def __init__(self, damage: int, captain: str | None = None):
        self.damage = damage
        if captain:
            self.captain = captain

    def hit(self):
        Starship.stats["hits"] = Starship.stats.get("hits", 0) + 1


enterprise_d = Starship(3000)
enterprise_d.stats = {}  # E: cannot access via instance
Starship.stats = {}  # OK


# > As a matter of convenience (and convention), instance variables can be
# > annotated in __init__ or other methods, rather than in the class.


class Box(Generic[T]):
    def __init__(self, content) -> None:
        self.content: T = content


# Tests for ClassVar in Protocol


class ProtoA(Protocol):
    x: ClassVar[str]
    y: ClassVar[str]
    z: CV = [""]


class ProtoAImpl:
    x = ""

    def __init__(self) -> None:
        self.y = ""


a: ProtoA = ProtoAImpl()  # E: y is not a ClassVar


class ProtoB(Protocol):
    x: int = 3
    y: int
    z: ClassVar[int]

    @classmethod
    def method1(cls) -> None:
        return None

    @staticmethod
    def method2() -> None:
        return None


class ProtoBImpl(ProtoB):
    y = 0
    z = 0


b_x: int = ProtoBImpl.x
b_y: int = ProtoBImpl.y
b_z: int = ProtoBImpl.z

b_m1 = ProtoBImpl.method1
b_m2 = ProtoBImpl.method2
