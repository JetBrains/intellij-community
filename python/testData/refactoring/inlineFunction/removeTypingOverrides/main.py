from typing import overload


class MyClass:
    def __init__(self, my_val):
        self.my_val = my_val

    @overload
    def method(self, x: int) -> int:
        pass

    @overload
    def method(self, x: str) -> str:
        pass

    def method(self, x):
        print(self.my_val)
        print(x)
        return x


my_class = MyClass(1)
res = my_class.met<caret>hod(2)