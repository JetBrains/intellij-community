from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal

from openpyxl.descriptors.base import Alias, Integer, _ConvertibleToInt
from openpyxl.descriptors.serialisable import Serialisable

class SheetBackgroundPicture(Serialisable):
    tagname: ClassVar[str]
    id: Incomplete
    def __init__(self, id) -> None: ...

class DrawingHF(Serialisable):
    id: Incomplete
    lho: Integer[Literal[True]]
    leftHeaderOddPages: Alias
    lhe: Integer[Literal[True]]
    leftHeaderEvenPages: Alias
    lhf: Integer[Literal[True]]
    leftHeaderFirstPage: Alias
    cho: Integer[Literal[True]]
    centerHeaderOddPages: Alias
    che: Integer[Literal[True]]
    centerHeaderEvenPages: Alias
    chf: Integer[Literal[True]]
    centerHeaderFirstPage: Alias
    rho: Integer[Literal[True]]
    rightHeaderOddPages: Alias
    rhe: Integer[Literal[True]]
    rightHeaderEvenPages: Alias
    rhf: Integer[Literal[True]]
    rightHeaderFirstPage: Alias
    lfo: Integer[Literal[True]]
    leftFooterOddPages: Alias
    lfe: Integer[Literal[True]]
    leftFooterEvenPages: Alias
    lff: Integer[Literal[True]]
    leftFooterFirstPage: Alias
    cfo: Integer[Literal[True]]
    centerFooterOddPages: Alias
    cfe: Integer[Literal[True]]
    centerFooterEvenPages: Alias
    cff: Integer[Literal[True]]
    centerFooterFirstPage: Alias
    rfo: Integer[Literal[True]]
    rightFooterOddPages: Alias
    rfe: Integer[Literal[True]]
    rightFooterEvenPages: Alias
    rff: Integer[Literal[True]]
    rightFooterFirstPage: Alias
    def __init__(
        self,
        id: Incomplete | None = None,
        lho: _ConvertibleToInt | None = None,
        lhe: _ConvertibleToInt | None = None,
        lhf: _ConvertibleToInt | None = None,
        cho: _ConvertibleToInt | None = None,
        che: _ConvertibleToInt | None = None,
        chf: _ConvertibleToInt | None = None,
        rho: _ConvertibleToInt | None = None,
        rhe: _ConvertibleToInt | None = None,
        rhf: _ConvertibleToInt | None = None,
        lfo: _ConvertibleToInt | None = None,
        lfe: _ConvertibleToInt | None = None,
        lff: _ConvertibleToInt | None = None,
        cfo: _ConvertibleToInt | None = None,
        cfe: _ConvertibleToInt | None = None,
        cff: _ConvertibleToInt | None = None,
        rfo: _ConvertibleToInt | None = None,
        rfe: _ConvertibleToInt | None = None,
        rff: _ConvertibleToInt | None = None,
    ) -> None: ...
