"""
Tests for advanced or special-case usage of the typing.Self type.
"""

from typing import assert_type, Self


class ParentA:
    # Test for property that returns Self.
    @property
    def prop1(self) -> Self:
        ...

class ChildA(ParentA):
    ...


assert_type(ParentA().prop1, ParentA)
assert_type(ChildA().prop1, ChildA)


# Test for a child that accesses an attribute within a parent
# whose type is annotated using Self.
class ParentB:
    a: list[Self]

    @classmethod
    def method1(cls) -> Self:
        ...

class ChildB(ParentB):
    b: int = 0

    def method2(self) -> None:
        assert_type(self, Self)
        assert_type(self.a, list[Self])
        assert_type(self.a[0], Self)
        assert_type(self.method1(), Self)

    @classmethod
    def method3(cls) -> None:
        assert_type(cls, type[Self])
        assert_type(cls.a, list[Self])
        assert_type(cls.a[0], Self)
        assert_type(cls.method1(), Self)
