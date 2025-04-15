from dataclasses import dataclass
from typing import Generic

from typing_extensions import TypeVar

T = TypeVar('T', default=int)

@dataclass
class MyDataclass(Generic[T]):
    x: T

MyDataclass(x=1)