from typing import overload


class A:
    @overload
    def foo(self, value: int) -> str:
        pass

    def foo(self, value):
        return None

    @overload
    def foo(self, value: str) -> str:
        pass

    def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>(self, value):
        return None