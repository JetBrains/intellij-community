from typing_extensions import Unpack

from typing import TypeVarTuple
from typing import Generic

Shape = TypeVarTuple("Shape")


class Array(Generic[Unp<caret>ack[Shape]]):
    ...