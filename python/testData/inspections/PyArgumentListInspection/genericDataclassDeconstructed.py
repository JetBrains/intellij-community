import dataclasses
from typing import Generic, TypeVar

T = TypeVar('T')


@dataclasses.dataclass
class D(Generic[T]):
    attr: T


a = D
c = a("foo")
