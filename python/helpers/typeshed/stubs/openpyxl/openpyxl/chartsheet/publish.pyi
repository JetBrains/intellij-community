from _typeshed import Incomplete
from typing import ClassVar, overload
from typing_extensions import Literal, TypeAlias

from openpyxl.descriptors.base import Bool, Integer, Set, String, _ConvertibleToBool, _ConvertibleToInt
from openpyxl.descriptors.serialisable import Serialisable

_WebPublishItemSourceType: TypeAlias = Literal[
    "sheet", "printArea", "autoFilter", "range", "chart", "pivotTable", "query", "label"
]

class WebPublishItem(Serialisable):
    tagname: ClassVar[str]
    id: Integer[Literal[False]]
    divId: String[Literal[False]]
    sourceType: Set[_WebPublishItemSourceType]
    sourceRef: String[Literal[False]]
    sourceObject: String[Literal[True]]
    destinationFile: String[Literal[False]]
    title: String[Literal[True]]
    autoRepublish: Bool[Literal[True]]
    @overload
    def __init__(
        self,
        id: _ConvertibleToInt,
        divId: str,
        sourceType: _WebPublishItemSourceType,
        sourceRef: str,
        sourceObject: str | None = None,
        *,
        destinationFile: str,
        title: str | None = None,
        autoRepublish: _ConvertibleToBool | None = None,
    ) -> None: ...
    @overload
    def __init__(
        self,
        id: _ConvertibleToInt,
        divId: str,
        sourceType: _WebPublishItemSourceType,
        sourceRef: str,
        sourceObject: str | None,
        destinationFile: str,
        title: str | None = None,
        autoRepublish: _ConvertibleToBool | None = None,
    ) -> None: ...

class WebPublishItems(Serialisable):
    tagname: ClassVar[str]
    count: Integer[Literal[True]]
    webPublishItem: Incomplete
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(self, count: _ConvertibleToInt | None = None, webPublishItem: Incomplete | None = None) -> None: ...
