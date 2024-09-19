from _typeshed import Incomplete
from collections.abc import Mapping

class DictType(dict[str, Incomplete]):
    def __init__(self, init: Mapping[str, Incomplete]) -> None: ...
