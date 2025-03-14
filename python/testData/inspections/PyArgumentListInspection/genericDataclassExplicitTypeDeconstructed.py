import dataclasses
from typing import Generic, TypeVar

T = TypeVar('T')


@dataclasses.dataclass
class D(Generic[T]):
    attr: T


a = D
b = a[str]
c = b("foo")
