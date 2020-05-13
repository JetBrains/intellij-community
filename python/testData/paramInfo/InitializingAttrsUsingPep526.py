import attr
import typing

@attr.s(auto_attribs=True)
class A:
    x: int
    y: str
    z: float = 0.0

A(<arg1>)


@attr.s(init=True, auto_attribs=True)
class A2:
    x: int
    y: str
    z: float = 0.0

A2(<arg2>)


@attr.s(init=False, auto_attribs=True)
class B1:
    x: int = 1
    y: str = "2"
    z: float = 0.0

B1(<arg3>)


@attr.s(init=False, auto_attribs=True)
class B2:
    x: int
    y: str
    z: float = 0.0

    def __init__(self, x: int):
        self.x = x
        self.y = str(x)
        self.z = 0.0

B2(<arg4>)


@attr.s(auto_attribs=True)
class C1:
    a: typing.ClassVar[int]
    b: int

C1(<arg5>)


@attr.dataclass
class D1:
    x: int
    y: str = "0"

D1(<arg6>)


@attr.dataclass
class E1:
    _x: int

E1(<arg7>)


@attr.dataclass
class F1:
    foo = "bar"  # <- has no type annotation, so doesn't count.
    baz: str

F1(<arg8>)


@attr.dataclass(unknown=True)
class G1:
    bar: str

G1(<arg9>)