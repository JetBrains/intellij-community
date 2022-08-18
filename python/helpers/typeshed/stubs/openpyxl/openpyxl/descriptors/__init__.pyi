from typing import Any

from .base import *
from .sequence import Sequence as Sequence

class MetaStrict(type):
    def __new__(cls, clsname, bases, methods): ...

class MetaSerialisable(type):
    def __new__(cls, clsname, bases, methods): ...

Strict: Any
_Serialiasable = Any
