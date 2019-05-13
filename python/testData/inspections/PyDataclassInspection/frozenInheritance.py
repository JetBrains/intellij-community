import attr
import dataclasses


@dataclasses.dataclass(frozen=True)
class A1:
    a: int = 1

@dataclasses.dataclass
class <error descr="Frozen dataclasses can not inherit non-frozen one and vice versa">B1</error>(A1):
    b: str = "2"


@dataclasses.dataclass
class A2:
    a: int = 1

@dataclasses.dataclass(<error descr="Frozen dataclasses can not inherit non-frozen one and vice versa">frozen=True</error>)
class B2(A2):
    b: str = "2"


@attr.s(frozen=True)
class A3:
    a: int = 1

@attr.s
class B3(A3):
    b: str = "2"


@attr.s
class A4:
    a: int = 1

@attr.s(frozen=True)
class B4(A4):
    b: str = "2"