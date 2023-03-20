from typing import Any

from openpyxl.descriptors.serialisable import Serialisable as Serialisable

class ChartsheetProperties(Serialisable):
    tagname: str
    published: Any
    codeName: Any
    tabColor: Any
    __elements__: Any
    def __init__(self, published: Any | None = ..., codeName: Any | None = ..., tabColor: Any | None = ...) -> None: ...
