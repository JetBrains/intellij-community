from attr import dataclass, evolve

@dataclass
class A1:
    a: int

class B1(A1):
    b: str

evolve(B1(1), <arg1>)


class A2:
    a: int

@dataclass
class B2(A2):
    b: str

evolve(B2("1"), <arg2>)


@dataclass
class A3:
    a: int

class B3(A3):
    def __init__(self, b: str):
        self.a = 10

evolve(B3("1"), <arg3>)


@dataclass
class A4:
    a: int

@undefined
class B4(A4):
    b: str

evolve(B4(), <arg4>)


@dataclass
class A5:
    x: int

class B5(A5):
    def __init__(self):
        pass

@dataclass
class C5(B5):
    z: str

evolve(C5(1, "2"), <arg5>)