import sys
from typing import TypeAlias, TypeVar

T = TypeVar("T")

if sys.version_info >= (3,):
    Alias: TypeAlias = list[T]
else:
    Alias: TypeAlias = set[T]

def f(x: T) -> Alias[T]:
    pass