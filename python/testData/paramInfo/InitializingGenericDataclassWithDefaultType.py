from dataclasses import dataclass
from typing import Generic

from typing import TypeVar

T = TypeVar('T', default=int)

@dataclass
class MyDataclass(Generic[T]):
    x: T

MyDataclass(<arg1>)