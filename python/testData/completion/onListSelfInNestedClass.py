from typing import Self

class OuterClass:
    class A:
        def foo(self) -> list[Self]:
            ...
    class B(A)
        def bar(self) -> Self:
            ...
OuterClass.B().foo()[0].<caret>