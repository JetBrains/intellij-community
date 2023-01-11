from typing import Self

class A:
    def foo(self) -> list[Self]:
        ...
class B(A)
    def bar(self) -> Self:
        ...
B().foo()[0].<caret>