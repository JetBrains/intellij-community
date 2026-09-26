from typing import Generic, TypeVar

T = TypeVar('T', default=int)

class StackOfIntsByDefault(Generic[T]):
    def pop(self) -> T: ...