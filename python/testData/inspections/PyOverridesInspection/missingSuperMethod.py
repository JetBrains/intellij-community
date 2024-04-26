from typing_extensions import override

class Parent:
    def foo(self) -> int:
        return 1

    def bar(self, x: str) -> str:
        return x

class Child(Parent):
    @override
    def foo(self) -> int:
        return 2

    <warning descr="Missing super method for override">@override</warning>
    def baz() -> int:
        return 1