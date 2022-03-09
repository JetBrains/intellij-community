from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Drawing(Serialisable):
    tagname: str
    id: Any
    def __init__(self, id: Any | None = ...) -> None: ...
