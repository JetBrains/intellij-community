from dataclasses import dataclass, replace

@dataclass
class A1:
    a: int

@dataclass
class B1(A1):
    b: str

replace(B1(1, "1"), <arg1>)


@dataclass(init=False)
class A2:
    a: int

@dataclass
class B2(A2):
    b: str

replace(B2(1, "1"), <arg2>)


@dataclass
class A3:
    a: int

@dataclass(init=False)
class B3(A3):
    b: str

replace(B3(1), <arg3>)


@dataclass(init=False)
class A4:
    a: int

@dataclass(init=False)
class B4(A4):
    b: str

replace(B4(), <arg4>)


@dataclass
class A5:
    x: Any = 15.0
    y: int = 0

@dataclass
class B5(A5):
    z: int = 10
    x: int = 15

replace(B5(), <arg5>)