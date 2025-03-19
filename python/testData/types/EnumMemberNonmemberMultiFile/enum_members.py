from enum import Enum, member, nonmember
from _enum_members import *

class Example(Enum):
    a = nonmember(1)
    A = member(lambda: 1)
    B = member(staticmethod(func))

    @member
    def method() -> None: ...