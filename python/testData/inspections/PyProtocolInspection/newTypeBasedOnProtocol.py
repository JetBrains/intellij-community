from typing import NewType, Protocol


class Id1(Protocol):
    code: int


UserId1 = NewType('UserId1', <warning descr="NewType cannot be used with protocol classes">Id1</warning>)


class Id2:
    code: int


UserId2 = NewType('UserId2', Id2)
