from _typeshed import Incomplete

from reportlab.pdfbase.pdfdoc import PDFObject

__all__ = ("AcroForm",)

class PDFFromString(PDFObject):
    def __init__(self, s) -> None: ...
    def format(self, document): ...

class RadioGroup(PDFObject):
    TU: Incomplete
    Ff: Incomplete
    kids: Incomplete
    T: Incomplete
    V: Incomplete
    def __init__(self, name, tooltip: str = "", fieldFlags: str = "noToggleToOff required radio") -> None: ...
    def format(self, doc): ...

class AcroForm(PDFObject):
    formFontNames: Incomplete
    referenceMap: Incomplete
    fonts: Incomplete
    fields: Incomplete
    sigFlags: Incomplete
    extras: Incomplete
    def __init__(self, canv, **kwds) -> None: ...
    @property
    def canv(self): ...
    def fontRef(self, f): ...
    def format(self, doc): ...
    def colorTuple(self, c): ...
    def streamFillColor(self, c): ...
    def streamStrokeColor(self, c): ...
    def checkboxAP(
        self,
        key,
        value,
        buttonStyle: str = "circle",
        shape: str = "square",
        fillColor=None,
        borderColor=None,
        textColor=None,
        borderWidth: int = 1,
        borderStyle: str = "solid",
        size: int = 20,
        dashLen: int = 3,
    ): ...
    @staticmethod
    def circleArcStream(size, r, arcs=(0, 1, 2, 3), rotated: bool = False): ...
    def zdMark(self, c, size, ds, iFontName): ...
    def getRef(self, obj): ...
    def getRefStr(self, obj): ...
    @staticmethod
    def stdColors(t, b, f): ...
    @staticmethod
    def varyColors(key, t, b, f): ...
    def checkForceBorder(
        self, x, y, width, height, forceBorder, shape, borderStyle, borderWidth, borderColor, fillColor
    ) -> None: ...
    def checkbox(
        self,
        checked: bool = False,
        buttonStyle: str = "check",
        shape: str = "square",
        fillColor=None,
        borderColor=None,
        textColor=None,
        borderWidth: int = 1,
        borderStyle: str = "solid",
        size: int = 20,
        x: int = 0,
        y: int = 0,
        tooltip=None,
        name=None,
        annotationFlags: str = "print",
        fieldFlags: str = "required",
        forceBorder: bool = False,
        relative: bool = False,
        dashLen: int = 3,
    ) -> None: ...
    def radio(
        self,
        value=None,
        selected: bool = False,
        buttonStyle: str = "circle",
        shape: str = "circle",
        fillColor=None,
        borderColor=None,
        textColor=None,
        borderWidth: int = 1,
        borderStyle: str = "solid",
        size: int = 20,
        x: int = 0,
        y: int = 0,
        tooltip=None,
        name=None,
        annotationFlags: str = "print",
        fieldFlags: str = "noToggleToOff required radio",
        forceBorder: bool = False,
        relative: bool = False,
        dashLen: int = 3,
    ) -> None: ...
    def makeStream(self, width, height, stream, **D): ...
    def txAP(
        self,
        key,
        value,
        iFontName,
        rFontName,
        fontSize,
        shape: str = "square",
        fillColor=None,
        borderColor=None,
        textColor=None,
        borderWidth: int = 1,
        borderStyle: str = "solid",
        width: int = 120,
        height: int = 36,
        dashLen: int = 3,
        wkind: str = "textfield",
        labels=[],
        I=[],
        sel_bg: str = "0.600006 0.756866 0.854904 rg",
        sel_fg: str = "0 g",
    ): ...
    def makeFont(self, fontName): ...
    def textfield(
        self,
        value: str = "",
        fillColor=None,
        borderColor=None,
        textColor=None,
        borderWidth: int = 1,
        borderStyle: str = "solid",
        width: int = 120,
        height: int = 36,
        x: int = 0,
        y: int = 0,
        tooltip=None,
        name=None,
        annotationFlags: str = "print",
        fieldFlags: str = "",
        forceBorder: bool = False,
        relative: bool = False,
        maxlen: int = 100,
        fontName=None,
        fontSize=None,
        dashLen: int = 3,
    ): ...
    def listbox(
        self,
        value: str = "",
        fillColor=None,
        borderColor=None,
        textColor=None,
        borderWidth: int = 1,
        borderStyle: str = "solid",
        width: int = 120,
        height: int = 36,
        x: int = 0,
        y: int = 0,
        tooltip=None,
        name=None,
        annotationFlags: str = "print",
        fieldFlags: str = "",
        forceBorder: bool = False,
        relative: bool = False,
        fontName=None,
        fontSize=None,
        dashLen: int = 3,
        maxlen=None,
        options=[],
    ): ...
    def choice(
        self,
        value: str = "",
        fillColor=None,
        borderColor=None,
        textColor=None,
        borderWidth: int = 1,
        borderStyle: str = "solid",
        width: int = 120,
        height: int = 36,
        x: int = 0,
        y: int = 0,
        tooltip=None,
        name=None,
        annotationFlags: str = "print",
        fieldFlags: str = "combo",
        forceBorder: bool = False,
        relative: bool = False,
        fontName=None,
        fontSize=None,
        dashLen: int = 3,
        maxlen=None,
        options=[],
    ): ...
    def checkboxRelative(self, **kwds) -> None: ...
    def radioRelative(self, **kwds) -> None: ...
    def textfieldRelative(self, **kwds) -> None: ...
    def listboxRelative(self, **kwds) -> None: ...
    def choiceRelative(self, **kwds) -> None: ...
    @property
    def encRefStr(self): ...

class CBMark:
    opNames: Incomplete
    opCount: Incomplete
    ops: Incomplete
    points: Incomplete
    slack: Incomplete
    def __init__(self, ops, points, bounds, slack: float = 0.05) -> None: ...
    def scaledRender(self, size, ds: int = 0): ...
