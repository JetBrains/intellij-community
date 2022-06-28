from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class UpDownBars(Serialisable):
    tagname: str
    gapWidth: Any
    upBars: Any
    downBars: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self, gapWidth: int = ..., upBars: Any | None = ..., downBars: Any | None = ..., extLst: Any | None = ...
    ) -> None: ...
