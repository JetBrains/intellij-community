from typing_extensions import Self

class A:
    def foo(self) -> Self:
        ...
class B(A):
    def bar(self) -> Self:
        ...
b = B()
b.f<the_ref>oo()