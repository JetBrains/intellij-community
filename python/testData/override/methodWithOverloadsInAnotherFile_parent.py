from typing import overload

class Foo(object):
    @overload
    def fun(self, s:str) -> str: pass

    @overload
    def fun(self, i:int) -> int: pass

    def fun(self, x):
        pass