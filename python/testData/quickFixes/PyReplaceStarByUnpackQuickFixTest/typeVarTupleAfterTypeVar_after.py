from __future__ import annotations
from typing_extensions import Unpack

from typing import Generic, TypeVar, TypeVarTuple

DType = TypeVar('DType')
Shape = TypeVarTuple('Shape')


class Array(Generic[DType, Unpack[Shape]]):
    pass
