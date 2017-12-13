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