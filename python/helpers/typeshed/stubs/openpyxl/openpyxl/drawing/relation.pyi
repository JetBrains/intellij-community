from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class ChartRelation(Serialisable):
    tagname: str
    namespace: Any
    id: Any
    def __init__(self, id) -> None: ...
