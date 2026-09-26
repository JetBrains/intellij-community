from __future__ import annotations

from typing import Generic, TypeVar, TypeVarTuple

DType = TypeVar('DType')
Shape = TypeVarTuple('Shape')


class Array(Generic[DType, <error descr="Python version 3.10 does not support starred expressions in subscriptions">*Sh<caret>ape</error>]):
    pass
