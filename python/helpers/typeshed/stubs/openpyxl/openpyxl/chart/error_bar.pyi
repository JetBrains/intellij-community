from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class ErrorBars(Serialisable):
    tagname: str
    errDir: Any
    direction: Any
    errBarType: Any
    style: Any
    errValType: Any
    size: Any
    noEndCap: Any
    plus: Any
    minus: Any
    val: Any
    spPr: Any
    graphicalProperties: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        errDir: Any | None = ...,
        errBarType: str = ...,
        errValType: str = ...,
        noEndCap: Any | None = ...,
        plus: Any | None = ...,
        minus: Any | None = ...,
        val: Any | None = ...,
        spPr: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...
