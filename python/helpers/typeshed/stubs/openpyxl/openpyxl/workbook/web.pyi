from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class WebPublishObject(Serialisable):
    tagname: str
    id: Any
    divId: Any
    sourceObject: Any
    destinationFile: Any
    title: Any
    autoRepublish: Any
    def __init__(
        self,
        id: Any | None = ...,
        divId: Any | None = ...,
        sourceObject: Any | None = ...,
        destinationFile: Any | None = ...,
        title: Any | None = ...,
        autoRepublish: Any | None = ...,
    ) -> None: ...

class WebPublishObjectList(Serialisable):
    tagname: str
    # Overwritten by property below
    # count: Integer
    webPublishObject: Any
    __elements__: Any
    def __init__(self, count: Any | None = ..., webPublishObject=...) -> None: ...
    @property
    def count(self): ...

class WebPublishing(Serialisable):
    tagname: str
    css: Any
    thicket: Any
    longFileNames: Any
    vml: Any
    allowPng: Any
    targetScreenSize: Any
    dpi: Any
    codePage: Any
    characterSet: Any
    def __init__(
        self,
        css: Any | None = ...,
        thicket: Any | None = ...,
        longFileNames: Any | None = ...,
        vml: Any | None = ...,
        allowPng: Any | None = ...,
        targetScreenSize: str = ...,
        dpi: Any | None = ...,
        codePage: Any | None = ...,
        characterSet: Any | None = ...,
    ) -> None: ...
