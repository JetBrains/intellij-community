from typing import Any

from openpyxl.descriptors import Typed
from openpyxl.descriptors.serialisable import Serialisable

class Title(Serialisable):
    tagname: str
    tx: Any
    text: Any
    layout: Any
    overlay: Any
    spPr: Any
    graphicalProperties: Any
    txPr: Any
    body: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        tx: Any | None = ...,
        layout: Any | None = ...,
        overlay: Any | None = ...,
        spPr: Any | None = ...,
        txPr: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

def title_maker(text): ...

class TitleDescriptor(Typed):
    expected_type: Any
    allow_none: bool
    def __set__(self, instance, value) -> None: ...
