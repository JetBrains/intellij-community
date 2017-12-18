import dataclasses
import typing

@dataclasses.dataclass
class A:
    x: int
    y: str
    z: float = 0.0

A(1, "a")
A(<warning descr="Expected type 'int', got 'str' instead">"a"</warning>, <warning descr="Expected type 'str', got 'int' instead">1</warning>)

A(1, "a", 1.0)
A(<warning descr="Expected type 'int', got 'str' instead">"a"</warning>, <warning descr="Expected type 'str', got 'int' instead">1</warning>, <warning descr="Expected type 'float', got 'str' instead">"b"</warning>)


@dataclasses.dataclass(init=True)
class A2:
    x: int
    y: str
    z: float = 0.0

A2(1, "a")
A2(<warning descr="Expected type 'int', got 'str' instead">"a"</warning>, <warning descr="Expected type 'str', got 'int' instead">1</warning>)

A2(1, "a", 1.0)
A2(<warning descr="Expected type 'int', got 'str' instead">"a"</warning>, <warning descr="Expected type 'str', got 'int' instead">1</warning>, <warning descr="Expected type 'float', got 'str' instead">"b"</warning>)


@dataclasses.dataclass(init=False)
class B1:
    x: int = 1
    y: str = "2"
    z: float = 0.0

B1(1)
B1("1")

B1(1, "a")
B1("a", 1)

B1(1, "a", 1.0)
B1("a", 1, "b")


@dataclasses.dataclass(init=False)
class B2:
    x: int
    y: str
    z: float = 0.0

    def __init__(self, x: int):
        self.x = x
        self.y = str(x)
        self.z = 0.0

B2(1)
B2(<warning descr="Expected type 'int', got 'str' instead">"1"</warning>)


@dataclasses.dataclass
class C1:
    a: typing.ClassVar[int]
    b: int

C1(1)
C1(<warning descr="Expected type 'int', got 'str' instead">"1"</warning>)


@dataclasses.dataclass
class C2:
    a: typing.ClassVar
    b: int

C2(1)
C2(<warning descr="Expected type 'int', got 'str' instead">"1"</warning>)


@dataclasses.dataclass
class D1:
    a: dataclasses.InitVar[int]
    b: int

D1(1, 2)
D1(<warning descr="Expected type 'int', got 'str' instead">"1"</warning>, <warning descr="Expected type 'int', got 'str' instead">"2"</warning>)