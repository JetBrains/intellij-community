import dataclasses

@dataclasses.dataclass
class A:
    x: int
    y: str
    z: float = 0.0

A(1, "a")
A(<warning descr="Expected type 'int', got 'str' instead">"a"</warning>, <warning descr="Expected type 'str', got 'int' instead">1</warning>)

A(1, "a", 1.0)
A(<warning descr="Expected type 'int', got 'str' instead">"a"</warning>, <warning descr="Expected type 'str', got 'int' instead">1</warning>, <warning descr="Expected type 'float', got 'str' instead">"b"</warning>)