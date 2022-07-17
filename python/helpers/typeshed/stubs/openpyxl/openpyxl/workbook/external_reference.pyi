from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class ExternalReference(Serialisable):
    tagname: str
    id: Any
    def __init__(self, id) -> None: ...
