import dataclasses
import typing

@dataclasses.dataclass
class A:
    x: int
    y: str
    z: float = 0.0

A(<warning descr="Parameter 'x' unfilled"><warning descr="Parameter 'y' unfilled">)</warning></warning>
A(1<warning descr="Parameter 'y' unfilled">)</warning>
A(1, "a")
A(1, "a", 1.0)
A(1, "a", 1.0, <warning descr="Unexpected argument">"b"</warning>)


@dataclasses.dataclass(init=True)
class A2:
    x: int
    y: str
    z: float = 0.0

A2(<warning descr="Parameter 'x' unfilled"><warning descr="Parameter 'y' unfilled">)</warning></warning>
A2(1<warning descr="Parameter 'y' unfilled">)</warning>
A2(1, "a")
A2(1, "a", 1.0)
A2(1, "a", 1.0, <warning descr="Unexpected argument">"b"</warning>)


@dataclasses.dataclass(init=False)
class B1:
    x: int = 1
    y: str = "2"
    z: float = 0.0

B1()
B1(<warning descr="Unexpected argument">1</warning>)
B1(<warning descr="Unexpected argument">1</warning>, <warning descr="Unexpected argument">"a"</warning>)
B1(<warning descr="Unexpected argument">1</warning>, <warning descr="Unexpected argument">"a"</warning>, <warning descr="Unexpected argument">1.0</warning>)
B1(<warning descr="Unexpected argument">1</warning>, <warning descr="Unexpected argument">"a"</warning>, <warning descr="Unexpected argument">1.0</warning>, <warning descr="Unexpected argument">"b"</warning>)


@dataclasses.dataclass(init=False)
class B2:
    x: int
    y: str
    z: float = 0.0

    def __init__(self, x: int):
        self.x = x
        self.y = str(x)
        self.z = 0.0

B2(<warning descr="Parameter 'x' unfilled">)</warning>
B2(1)
B2(1, <warning descr="Unexpected argument">2</warning>)


@dataclasses.dataclass
class C1:
    a: typing.ClassVar[int]
    b: int

C1(<warning descr="Parameter 'b' unfilled">)</warning>
C1(1)
C1(1, <warning descr="Unexpected argument">2</warning>)


@dataclasses.dataclass
class C2:
    a: typing.ClassVar
    b: int

C2(<warning descr="Parameter 'b' unfilled">)</warning>
C2(1)
C2(1, <warning descr="Unexpected argument">2</warning>)


@dataclasses.dataclass
class D1:
    a: dataclasses.InitVar[int]
    b: int

D1(<warning descr="Parameter 'a' unfilled"><warning descr="Parameter 'b' unfilled">)</warning></warning>
D1(1<warning descr="Parameter 'b' unfilled">)</warning>
D1(1, 2)
D1(1, 2, <warning descr="Unexpected argument">3</warning>)


@dataclasses.dataclass
class E1:
    a: int = dataclasses.field()
    b: int = dataclasses.field(init=True)
    c: int = dataclasses.field(init=False)
    d: int = dataclasses.field(default=1)
    e: int = dataclasses.field(default_factory=int)

E1(1<warning descr="Parameter 'b' unfilled">)</warning>
E1(1, 2)
E1(1, 2, 3)
E1(1, 2, 3, 4)
E1(1, 2, 3, 4, <warning descr="Unexpected argument">5</warning>)


@dataclasses.dataclass
class F1:
    foo = "bar"  # <- has no type annotation, so doesn't count.
    baz: str

F1(<warning descr="Parameter 'baz' unfilled">)</warning>
F1("1")
F1("1", <warning descr="Unexpected argument">"2"</warning>)
