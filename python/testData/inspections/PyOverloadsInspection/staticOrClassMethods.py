from typing import overload


class A:
    @overload
    def <warning descr="Overloads use @staticmethod inconsistently">foo</warning>(self, x: int, /): ...

    @staticmethod
    @overload
    def foo(x: str, /): ...

    def <warning descr="Overloads use @staticmethod inconsistently">foo</warning>(*args: object):
        pass

    @classmethod
    @overload
    def bar(cls, x: int, /): ...

    @overload
    def <warning descr="Overloads use @classmethod inconsistently">bar</warning>(self, x: str, /): ...

    def <warning descr="Overloads use @classmethod inconsistently">bar</warning>(*args: object):
        pass
