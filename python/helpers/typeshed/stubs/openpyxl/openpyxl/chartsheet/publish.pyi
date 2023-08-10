from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class WebPublishItem(Serialisable):
    tagname: str
    id: Any
    divId: Any
    sourceType: Any
    sourceRef: Any
    sourceObject: Any
    destinationFile: Any
    title: Any
    autoRepublish: Any
    def __init__(
        self,
        id: Any | None = ...,
        divId: Any | None = ...,
        sourceType: Any | None = ...,
        sourceRef: Any | None = ...,
        sourceObject: Any | None = ...,
        destinationFile: Any | None = ...,
        title: Any | None = ...,
        autoRepublish: Any | None = ...,
    ) -> None: ...

class WebPublishItems(Serialisable):
    tagname: str
    count: Any
    webPublishItem: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., webPublishItem: Any | None = ...) -> None: ...
