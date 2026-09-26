from bases import BaseFrozenWithFrozenDefault
from decorator import my_dataclass


@my_dataclass(frozen=True)
class B1(BaseFrozenWithFrozenDefault):
    b: str = "1"

<error descr="'BaseFrozenWithFrozenDefault' object attribute 'a' is read-only">BaseFrozenWithFrozenDefault().a</error> = 2
<error descr="'B1' object attribute 'a' is read-only">B1().a</error> = 2
<error descr="'B1' object attribute 'b' is read-only">B1().b</error> = "2"
