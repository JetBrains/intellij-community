from typing import Self


class HasNestedFunction:
    x: int = 42

    def foo(self) -> None:
        def nested(z: int, inner_self: Self) -> Self:
            print(inner_self.<caret>)
            return inner_self

        nested(42, self)