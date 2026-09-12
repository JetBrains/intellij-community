from enum import Enum


class MyEnum(str, Enum):
    def __init__(self, *args):
        self.is_good = None

    a = "a"


def test(actual: MyEnum):
    actual.is_good
