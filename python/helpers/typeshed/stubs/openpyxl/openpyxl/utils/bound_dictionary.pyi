from collections import defaultdict
from typing import Any

class BoundDictionary(defaultdict[Any, Any]):
    reference: Any
    def __init__(self, reference: Any | None = ..., *args, **kw) -> None: ...
    def __getitem__(self, key): ...
