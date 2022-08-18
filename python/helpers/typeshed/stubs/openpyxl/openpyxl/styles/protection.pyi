from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Protection(Serialisable):
    tagname: str
    locked: Any
    hidden: Any
    def __init__(self, locked: bool = ..., hidden: bool = ...) -> None: ...
