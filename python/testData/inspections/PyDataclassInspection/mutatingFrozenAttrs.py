import attr


@attr.s(auto_attribs=True)
class B1:
    x: int
    y: str
    z: float = 0.0

B1.x = 5
b1 = B1(1, "2")
b1.x = 2
b1.y = "3"
b1.z = 1.0
del b1.x
del b1.y
del b1.z


@attr.s(frozen=False, auto_attribs=True)
class B2:
    x: int
    y: str
    z: float = 0.0

B2.x = 5
b2 = B2(1, "2")
b2.x = 2
b2.y = "3"
b2.z = 1.0
del b2.x
del b2.y
del b2.z


@attr.s(frozen=True, auto_attribs=True)
class B3:
    x: int
    y: str
    z: float = 0.0

B3.x = 5
b3 = B3(1, "2")
<error descr="'B3' object attribute 'x' is read-only">b3.x</error> = 2
<error descr="'B3' object attribute 'y' is read-only">b3.y</error> = "3"
<error descr="'B3' object attribute 'z' is read-only">b3.z</error> = 1.0
del <error descr="'B3' object attribute 'x' is read-only">b3.x</error>
del <error descr="'B3' object attribute 'y' is read-only">b3.y</error>
del <error descr="'B3' object attribute 'z' is read-only">b3.z</error>