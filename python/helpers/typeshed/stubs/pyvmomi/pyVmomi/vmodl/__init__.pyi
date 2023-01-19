from typing import Any

from .fault import *
from .query import *

class DynamicData: ...

class DynamicProperty:
    def __init__(self, *, name: str = ..., val: Any = ...) -> None: ...
    name: str
    val: Any

class ManagedObject: ...

class KeyAnyValue(DynamicData):
    key: str
    value: Any

class LocalizableMessage(DynamicData):
    key: str
    arg: list[KeyAnyValue] | None
    message: str | None

class MethodFault(DynamicData, Exception):
    msg: str | None
    faultCause: MethodFault | None
    faultMessage: list[LocalizableMessage] | None
