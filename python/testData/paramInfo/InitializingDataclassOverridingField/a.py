from dataclasses import dataclass, field


@dataclass
class A1:
    x: Any = 15.0
    y: int = 0

@dataclass
class B1(A1):
    z: int = 10
    x: int = 15

B1(<arg1>)


@dataclass
class A2:
    a: int
    aa: int

@dataclass
class B2(A2):
    aa: int = field(init=False)

    def __post_init__(self):
        self.aa = 44

B2(<arg2>)