from enum import EnumMeta, member

class Color(metaclass=EnumMeta):
    RED = 1

    R = RED
    r = R

    @member
    def foo(x: int) -> int:
        pass

    bar = foo
    buz = bar