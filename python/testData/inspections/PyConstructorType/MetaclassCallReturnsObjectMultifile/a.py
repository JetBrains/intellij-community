from typing import assert_type, Self
from m import Meta


class MyClass(metaclass=Meta):
    def __new__(cls, p) -> Self: ...


assert_type(MyClass(), object)
MyClass(<warning descr="Unexpected argument">1</warning>)
