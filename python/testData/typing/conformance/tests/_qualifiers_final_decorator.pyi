"""
Support stub file for @final tests.
"""

from typing import final, overload


class Base3:
    # > For overloaded methods, @final should be placed on the implementation
    # > (or on the first overload, for stubs):
    @final
    @overload
    def method(self, x: int) -> int:
        ...

    @overload
    def method(self, x: str) -> str:
        ...

class Base4:
    # (Swap the order of overload and final decorators.)
    @overload
    @final
    def method(self, x: int) -> int:
        ...

    @overload
    def method(self, x: str) -> str:
        ...
