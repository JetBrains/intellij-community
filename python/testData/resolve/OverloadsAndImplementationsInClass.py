from typing import overload


class A:
    @overload
    def foo(self, value: None) -> None:
        pass

    def foo(self, value):
        return None

    @overload
    def foo(self, value: int) -> str:
        pass

    def foo(self, value):
        return None

    @overload
    def foo(self, value: str) -> str:
        pass


A().foo("abc")
     <ref>