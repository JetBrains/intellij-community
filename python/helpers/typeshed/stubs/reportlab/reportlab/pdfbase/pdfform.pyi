from _typeshed import Incomplete

from reportlab.pdfbase.pdfdoc import PDFObject

def textFieldAbsolute(canvas, title, x, y, width, height, value: str = "", maxlen: int = 1000000, multiline: int = 0): ...
def textFieldRelative(canvas, title, xR, yR, width, height, value: str = "", maxlen: int = 1000000, multiline: int = 0): ...
def buttonFieldAbsolute(canvas, title, value, x, y, width: float = 16.7704, height: float = 14.907): ...
def buttonFieldRelative(canvas, title, value, xR, yR, width: float = 16.7704, height: float = 14.907): ...
def selectFieldAbsolute(canvas, title, value, options, x, y, width, height) -> None: ...
def selectFieldRelative(canvas, title, value, options, xR, yR, width, height): ...
def getForm(canvas): ...

class AcroForm(PDFObject):
    fields: Incomplete
    def __init__(self) -> None: ...
    def textField(
        self, canvas, title, xmin, ymin, xmax, ymax, value: str = "", maxlen: int = 1000000, multiline: int = 0
    ) -> None: ...
    def selectField(self, canvas, title, value, options, xmin, ymin, xmax, ymax) -> None: ...
    def buttonField(self, canvas, title, value, xmin, ymin, width: float = 16.7704, height: float = 14.907) -> None: ...
    def format(self, document): ...

FormPattern: Incomplete

def FormFontsDictionary(): ...
def FormResources(): ...

ZaDbPattern: Incomplete
FormResourcesDictionaryPattern: Incomplete
FORMFONTNAMES: Incomplete
EncodingPattern: Incomplete
PDFDocEncodingPattern: Incomplete

def FormFont(BaseFont, Name): ...

FormFontPattern: Incomplete

def resetPdfForm() -> None: ...
def TextField(
    title,
    value,
    xmin,
    ymin,
    xmax,
    ymax,
    page,
    maxlen: int = 1000000,
    font: str = "Helvetica-Bold",
    fontsize: int = 9,
    R: int = 0,
    G: int = 0,
    B: float = 0.627,
    multiline: int = 0,
): ...

TextFieldPattern: Incomplete

def SelectField(
    title,
    value,
    options,
    xmin,
    ymin,
    xmax,
    ymax,
    page,
    font: str = "Helvetica-Bold",
    fontsize: int = 9,
    R: int = 0,
    G: int = 0,
    B: float = 0.627,
): ...

SelectFieldPattern: Incomplete

def ButtonField(title, value, xmin, ymin, page, width: float = 16.7704, height: float = 14.907): ...

ButtonFieldPattern: Incomplete

def buttonStreamDictionary(width: float = 16.7704, height: float = 14.907): ...
def ButtonStream(content, width: float = 16.7704, height: float = 14.907): ...
