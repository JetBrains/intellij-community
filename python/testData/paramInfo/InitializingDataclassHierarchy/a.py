from dataclasses import dataclass

@dataclass
class A1:
    a: int

@dataclass
class B1(A1):
    b: str

B1(<arg1>)


@dataclass(init=False)
class A2:
    a: int

@dataclass
class B2(A2):
    b: str

B2(<arg2>)


@dataclass
class A3:
    a: int

@dataclass(init=False)
class B3(A3):
    b: str

B3(<arg3>)


@dataclass(init=False)
class A4:
    a: int

@dataclass(init=False)
class B4(A4):
    b: str

B4(<arg4>)