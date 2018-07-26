from typing import overload

class Foo:
    @overload
    def fun(self, s:str) -> str: pass

    @overload
    def fun(self, i:int) -> int: pass

    def fun(self, x):
        pass

class B(Foo):
    <caret>pass