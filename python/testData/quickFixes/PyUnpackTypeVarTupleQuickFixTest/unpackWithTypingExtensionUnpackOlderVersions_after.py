from typing_extensions import Unpack

from typing import TypeVarTuple
from typing import Generic
from typing import Tuple

Shape = TypeVarTuple("Shape")


class Array(Generic[Unpack[Shape]]):
    def __init__(self, shape: Tuple[Un<caret>pack[Shape]]) -> None:
        self.shape = shape