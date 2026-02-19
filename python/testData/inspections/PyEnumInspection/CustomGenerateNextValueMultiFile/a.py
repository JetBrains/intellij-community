from enum import auto, Enum
from my_enum_base import MyEnumBase


class MyEnumDerived(MyEnumBase):
    FOO = auto()
    BAR = <warning descr="Type 'int' is not assignable to declared type 'MyVal'">2</warning>
