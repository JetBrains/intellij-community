from typing import Any, final
from weakref import ref

@final
class ValuedWeakRef(ref):
    value: Any

@final
class IdentRegistry:
    def __init__(self) -> None: ...
    def get_ident(self, obj: object) -> int: ...
    def __len__(self) -> int: ...
