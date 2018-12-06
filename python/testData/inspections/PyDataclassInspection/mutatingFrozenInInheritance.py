import attr
import dataclasses


@dataclasses.dataclass(frozen=True)
class A1:
    a: int = 1

@dataclasses.dataclass(frozen=True)
class B1(A1):
    b: str = "1"

<error descr="'A1' object attribute 'a' is read-only">A1().a</error> = 2
<error descr="'B1' object attribute 'a' is read-only">B1().a</error> = 2
<error descr="'B1' object attribute 'b' is read-only">B1().b</error> = "2"


@attr.dataclass(frozen=True)
class A2:
    a: int = 1

@attr.dataclass(frozen=True)
class B2(A2):
    b: str = "2"

<error descr="'A2' object attribute 'a' is read-only">A2().a</error> = 2
<error descr="'B2' object attribute 'a' is read-only"><error descr="'B2' object attribute 'a' is read-only">B2().a</error></error> = 2
<error descr="'B2' object attribute 'b' is read-only"><error descr="'B2' object attribute 'b' is read-only">B2().b</error></error> = "2"


@attr.s(frozen=True)
class A3:
    a: int = 1

@attr.s
class B3(A3):
    b: str = "2"

<error descr="'A3' object attribute 'a' is read-only">A3().a</error> = 2
<error descr="'B3' object attribute 'a' is read-only">B3().a</error> = 2
<error descr="'B3' object attribute 'b' is read-only">B3().b</error> = "2"


@attr.s
class A4:
    a: int = 1

@attr.s(frozen=True)
class B4(A4):
    b: str = "2"

A4().a = 2
<error descr="'B4' object attribute 'a' is read-only">B4().a</error> = 2
<error descr="'B4' object attribute 'b' is read-only">B4().b</error> = "2"