from typing import overload

from methodWithOverloadsInAnotherFile_parent import Foo

class B(Foo):
    @overload
    def fun(self, s:str) -> str: pass

    @overload
    def fun(self, i:int) -> int: pass

    def fun(self, x):
        super().fun(x)

