from dataclasses import dataclass
from typing import Generic

from typing import TypeVar

T = TypeVar('T')

@dataclass
class MyDataclass(Generic[T]):
    x: T

MyDataclass(<arg1>)