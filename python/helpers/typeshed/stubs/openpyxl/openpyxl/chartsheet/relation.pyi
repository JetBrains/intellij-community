from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class SheetBackgroundPicture(Serialisable):
    tagname: str
    id: Any
    def __init__(self, id) -> None: ...

class DrawingHF(Serialisable):
    id: Any
    lho: Any
    leftHeaderOddPages: Any
    lhe: Any
    leftHeaderEvenPages: Any
    lhf: Any
    leftHeaderFirstPage: Any
    cho: Any
    centerHeaderOddPages: Any
    che: Any
    centerHeaderEvenPages: Any
    chf: Any
    centerHeaderFirstPage: Any
    rho: Any
    rightHeaderOddPages: Any
    rhe: Any
    rightHeaderEvenPages: Any
    rhf: Any
    rightHeaderFirstPage: Any
    lfo: Any
    leftFooterOddPages: Any
    lfe: Any
    leftFooterEvenPages: Any
    lff: Any
    leftFooterFirstPage: Any
    cfo: Any
    centerFooterOddPages: Any
    cfe: Any
    centerFooterEvenPages: Any
    cff: Any
    centerFooterFirstPage: Any
    rfo: Any
    rightFooterOddPages: Any
    rfe: Any
    rightFooterEvenPages: Any
    rff: Any
    rightFooterFirstPage: Any
    def __init__(
        self,
        id: Any | None = ...,
        lho: Any | None = ...,
        lhe: Any | None = ...,
        lhf: Any | None = ...,
        cho: Any | None = ...,
        che: Any | None = ...,
        chf: Any | None = ...,
        rho: Any | None = ...,
        rhe: Any | None = ...,
        rhf: Any | None = ...,
        lfo: Any | None = ...,
        lfe: Any | None = ...,
        lff: Any | None = ...,
        cfo: Any | None = ...,
        cfe: Any | None = ...,
        cff: Any | None = ...,
        rfo: Any | None = ...,
        rfe: Any | None = ...,
        rff: Any | None = ...,
    ) -> None: ...
