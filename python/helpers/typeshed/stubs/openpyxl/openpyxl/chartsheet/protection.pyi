from typing import Any

from openpyxl.descriptors.serialisable import Serialisable
from openpyxl.worksheet.protection import _Protected

class ChartsheetProtection(Serialisable, _Protected):
    tagname: str
    algorithmName: Any
    hashValue: Any
    saltValue: Any
    spinCount: Any
    content: Any
    objects: Any
    __attrs__: Any
    password: Any
    def __init__(
        self,
        content: Any | None = ...,
        objects: Any | None = ...,
        hashValue: Any | None = ...,
        spinCount: Any | None = ...,
        saltValue: Any | None = ...,
        algorithmName: Any | None = ...,
        password: Any | None = ...,
    ) -> None: ...
