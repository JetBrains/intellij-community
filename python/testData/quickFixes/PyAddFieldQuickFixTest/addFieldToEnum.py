from enum import Enum


class MyEnum(str, Enum):
    a = "a"


def test(actual: MyEnum):
    actual.<caret><warning descr="Unresolved attribute reference 'is_good' for class 'MyEnum'">is_good</warning>
