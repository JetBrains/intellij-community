from typing import Self
from m import Meta


class MyClass(metaclass=Meta):
    def __new__(cls, p) -> Self: ...


expr = MyClass()
