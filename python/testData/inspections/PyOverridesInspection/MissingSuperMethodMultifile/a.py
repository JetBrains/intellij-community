from mod import Parent
from typing_extensions import override


class Child(Parent):
    @override
    def foo(self, x: str, y: int) -> str:
        return 2

    <warning descr="Missing super method for override">@override</warning>
    def baz(self, x: str, y: int) -> str:
        return "1"
