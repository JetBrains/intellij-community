from typing import Self

class A:
    def foo(self) -> Self:
        ...
class B(A):
    def bar(self) -> Self:
        ...
B().f<the_ref>oo()