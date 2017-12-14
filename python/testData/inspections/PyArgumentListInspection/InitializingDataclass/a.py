import dataclasses

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
B1<warning descr="Unexpected argument(s)Possible callees:object(self: object)object.__new__(cls: object)">(1)</warning>
B1<warning descr="Unexpected argument(s)Possible callees:object(self: object)object.__new__(cls: object)">(1, "a")</warning>
B1<warning descr="Unexpected argument(s)Possible callees:object(self: object)object.__new__(cls: object)">(1, "a", 1.0)</warning>
B1<warning descr="Unexpected argument(s)Possible callees:object(self: object)object.__new__(cls: object)">(1, "a", 1.0, "b")</warning>


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
