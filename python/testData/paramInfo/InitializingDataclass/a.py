import dataclasses
import typing

@dataclasses.dataclass
class A:
    x: int
    y: str
    z: float = 0.0

A(<arg1>)


@dataclasses.dataclass(init=True)
class A2:
    x: int
    y: str
    z: float = 0.0

A2(<arg2>)


@dataclasses.dataclass(init=False)
class B1:
    x: int = 1
    y: str = "2"
    z: float = 0.0

B1(<arg3>)


@dataclasses.dataclass(init=False)
class B2:
    x: int
    y: str
    z: float = 0.0

    def __init__(self, x: int):
        self.x = x
        self.y = str(x)
        self.z = 0.0

B2(<arg4>)


@dataclasses.dataclass
class C1:
    a: typing.ClassVar[int]
    b: int

C1(<arg5>)


@dataclasses.dataclass
class C2:
    a: typing.ClassVar
    b: int

C2(<arg6>)


@dataclasses.dataclass
class D1:
    a: dataclasses.InitVar[int]
    b: int

D1(<arg7>)


@dataclasses.dataclass
class E1:
    a: int = dataclasses.field()
    b: int = dataclasses.field(init=True)
    c: int = dataclasses.field(init=False)
    d: int = dataclasses.field(default=1)
    e: int = dataclasses.field(default_factory=int)

E1(<arg8>)


@dataclasses.dataclass
class F1:
    x: int
    y: str
    z: float = 0.0

    @classmethod
    def from_str(cls, string):
        return cls(<arg9>)

    def to_str(self):
        return self(<arg10>)
