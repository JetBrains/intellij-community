from typing import overload, TypeVar

class Other:
    pass

class Base:
    @overload
    @classmethod
    def <warning descr="Signature of this @overload-decorated method is not compatible with the implementation">create</warning>(cls: type[Other], x: int) -> "Other": ...

    @overload
    @classmethod
    def create(cls, x: str) -> "Base": ...

    @classmethod
    def create(cls, x: int | str) -> "Base":
        return cls()

T = TypeVar("T", bound=Other)

class Base:
    @overload
    def <warning descr="Signature of this @overload-decorated method is not compatible with the implementation">foo</warning>(self: T, x: int) -> T: ...

    @overload
    def foo(self, x: str) -> str: ...

    def foo(self, x: int | str) -> "Base | str":
        return self