from typing import overload, Any
class Descriptor:
    @overload
    def __get__(self, __obj: None, __owner: Any) -> str:
        ...

    @overload
    def __get__(self, __obj: object, __owner: Any) -> int:
        ...

    def __get__(self, __obj: object | None, __owner: Any) -> int | str:
        raise NotImplementedError

class NotDescriptor:
    @overload
    def <warning descr="This overload overlaps overload 2 with incompatible return typeConflicting signature: '(self: Self@NotDescriptor, __obj: None, __owner: Any) -> str'">foo</warning>(self, __obj: None, __owner: Any) -> str:
        ...

    @overload
    def foo(self, __obj: object, __owner: Any) -> int:
        ...

    def foo(self, __obj: object | None, __owner: Any) -> int | str:
        raise NotImplementedError