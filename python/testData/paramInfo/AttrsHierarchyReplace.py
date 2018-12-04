from attr import dataclass, evolve

@dataclass
class A1:
    a: int

@dataclass
class B1(A1):
    b: str

evolve(B1(1, "1"), <arg1>)


@dataclass(init=False)
class A2:
    a: int

@dataclass
class B2(A2):
    b: str

evolve(B2(1, "1"), <arg2>)


@dataclass
class A3:
    a: int

@dataclass(init=False)
class B3(A3):
    b: str

evolve(B3(1), <arg3>)


@dataclass(init=False)
class A4:
    a: int

@dataclass(init=False)
class B4(A4):
    b: str

evolve(B4(), <arg4>)