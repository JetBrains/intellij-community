from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class PictureOptions(Serialisable):
    tagname: str
    applyToFront: Any
    applyToSides: Any
    applyToEnd: Any
    pictureFormat: Any
    pictureStackUnit: Any
    __elements__: Any
    def __init__(
        self,
        applyToFront: Any | None = ...,
        applyToSides: Any | None = ...,
        applyToEnd: Any | None = ...,
        pictureFormat: Any | None = ...,
        pictureStackUnit: Any | None = ...,
    ) -> None: ...
