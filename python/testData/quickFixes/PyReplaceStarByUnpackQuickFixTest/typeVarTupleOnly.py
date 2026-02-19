from typing import TypeVarTuple
from typing import Generic

Shape = TypeVarTuple("Shape")


class Array(Generic[<error descr="Python version 3.10 does not support starred expressions in subscriptions">*Sh<caret>ape</error>]):
    ...