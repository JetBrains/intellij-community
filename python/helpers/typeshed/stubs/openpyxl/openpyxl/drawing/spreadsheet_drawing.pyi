from _typeshed import Incomplete
from typing import ClassVar
from typing_extensions import Literal, TypeAlias

from openpyxl.descriptors.base import Alias, Bool, NoneSet, Typed, _ConvertibleToBool, _ConvertibleToInt
from openpyxl.descriptors.nested import NestedText
from openpyxl.descriptors.serialisable import Serialisable
from openpyxl.drawing.connector import Shape
from openpyxl.drawing.graphic import GraphicFrame, GroupShape
from openpyxl.drawing.picture import PictureFrame
from openpyxl.drawing.xdr import XDRPoint2D, XDRPositiveSize2D

_TwoCellAnchorEditAs: TypeAlias = Literal["twoCell", "oneCell", "absolute"]

class AnchorClientData(Serialisable):
    fLocksWithSheet: Bool[Literal[True]]
    fPrintsWithSheet: Bool[Literal[True]]
    def __init__(
        self, fLocksWithSheet: _ConvertibleToBool | None = None, fPrintsWithSheet: _ConvertibleToBool | None = None
    ) -> None: ...

class AnchorMarker(Serialisable):
    tagname: ClassVar[str]
    col: NestedText[int, Literal[False]]
    colOff: NestedText[int, Literal[False]]
    row: NestedText[int, Literal[False]]
    rowOff: NestedText[int, Literal[False]]
    def __init__(
        self, col: _ConvertibleToInt = 0, colOff: _ConvertibleToInt = 0, row: _ConvertibleToInt = 0, rowOff: _ConvertibleToInt = 0
    ) -> None: ...

class _AnchorBase(Serialisable):
    sp: Typed[Shape, Literal[True]]
    shape: Alias
    grpSp: Typed[GroupShape, Literal[True]]
    groupShape: Alias
    graphicFrame: Typed[GraphicFrame, Literal[True]]
    cxnSp: Typed[Shape, Literal[True]]
    connectionShape: Alias
    pic: Typed[PictureFrame, Literal[True]]
    contentPart: Incomplete
    clientData: Typed[AnchorClientData, Literal[False]]
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self,
        clientData: AnchorClientData | None = None,
        sp: Shape | None = None,
        grpSp: GroupShape | None = None,
        graphicFrame: GraphicFrame | None = None,
        cxnSp: Shape | None = None,
        pic: PictureFrame | None = None,
        contentPart: Incomplete | None = None,
    ) -> None: ...

class AbsoluteAnchor(_AnchorBase):
    tagname: ClassVar[str]
    pos: Typed[XDRPoint2D, Literal[False]]
    ext: Typed[XDRPositiveSize2D, Literal[False]]
    # Same as parent
    # sp = _AnchorBase.sp
    # grpSp = _AnchorBase.grpSp
    # graphicFrame = _AnchorBase.graphicFrame
    # cxnSp = _AnchorBase.cxnSp
    # pic = _AnchorBase.pic
    # contentPart = _AnchorBase.contentPart
    # clientData = _AnchorBase.clientData
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(self, pos: XDRPoint2D | None = None, ext: XDRPositiveSize2D | None = None, **kw) -> None: ...

class OneCellAnchor(_AnchorBase):
    tagname: ClassVar[str]
    _from: Typed[AnchorMarker, Literal[False]]  # Not private. Avoids name clash
    ext: Typed[XDRPositiveSize2D, Literal[False]]
    # Same as parent
    # sp = _AnchorBase.sp
    # grpSp = _AnchorBase.grpSp
    # graphicFrame = _AnchorBase.graphicFrame
    # cxnSp = _AnchorBase.cxnSp
    # pic = _AnchorBase.pic
    # contentPart = _AnchorBase.contentPart
    # clientData = _AnchorBase.clientData
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(self, _from: AnchorMarker | None = None, ext: XDRPositiveSize2D | None = None, **kw) -> None: ...

class TwoCellAnchor(_AnchorBase):
    tagname: ClassVar[str]
    editAs: NoneSet[_TwoCellAnchorEditAs]
    _from: Typed[AnchorMarker, Literal[False]]  # Not private. Avoids name clash
    to: Typed[AnchorMarker, Literal[False]]
    # Same as parent
    # sp = _AnchorBase.sp
    # grpSp = _AnchorBase.grpSp
    # graphicFrame = _AnchorBase.graphicFrame
    # cxnSp = _AnchorBase.cxnSp
    # pic = _AnchorBase.pic
    # contentPart = _AnchorBase.contentPart
    # clientData = _AnchorBase.clientData
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self,
        editAs: _TwoCellAnchorEditAs | Literal["none"] | None = None,
        _from: AnchorMarker | None = None,
        to: AnchorMarker | None = None,
        **kw,
    ) -> None: ...

class SpreadsheetDrawing(Serialisable):
    tagname: ClassVar[str]
    mime_type: str
    PartName: str
    twoCellAnchor: Incomplete
    oneCellAnchor: Incomplete
    absoluteAnchor: Incomplete
    __elements__: ClassVar[tuple[str, ...]]
    charts: Incomplete
    images: Incomplete
    def __init__(self, twoCellAnchor=(), oneCellAnchor=(), absoluteAnchor=()) -> None: ...
    def __hash__(self) -> int: ...
    def __bool__(self) -> bool: ...
    @property
    def path(self) -> str: ...
