from typing import Self


class HasNestedFunction:
    x: int = 42

    def foo(self, inner_self: Self) -> None:
        print(inner_self.<caret>)
