from typing import overload


class A:
    @overload
    def foo(self, value: None) -> None:
        pass

    @overload
    def foo(self, value: int) -> str:
        pass

    @overload
    def foo(self, value: str) -> str:
        pass

    def foo(self, <weak_warning descr="Parameter 'value' value is not used">value</weak_warning>):
        return None