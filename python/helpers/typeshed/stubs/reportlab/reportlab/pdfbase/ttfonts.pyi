from _typeshed import Incomplete
from typing import Final, NamedTuple

from reportlab.pdfbase import pdfdoc, pdfmetrics

__version__: Final[str]

class TTFError(pdfdoc.PDFError): ...

def SUBSETN(n, table=...): ...
def makeToUnicodeCMap(fontname, subset): ...
def splice(stream, offset, value): ...

GF_ARG_1_AND_2_ARE_WORDS: Incomplete
GF_ARGS_ARE_XY_VALUES: Incomplete
GF_ROUND_XY_TO_GRID: Incomplete
GF_WE_HAVE_A_SCALE: Incomplete
GF_RESERVED: Incomplete
GF_MORE_COMPONENTS: Incomplete
GF_WE_HAVE_AN_X_AND_Y_SCALE: Incomplete
GF_WE_HAVE_A_TWO_BY_TWO: Incomplete
GF_WE_HAVE_INSTRUCTIONS: Incomplete
GF_USE_MY_METRICS: Incomplete
GF_OVERLAP_COMPOUND: Incomplete
GF_SCALED_COMPONENT_OFFSET: Incomplete
GF_UNSCALED_COMPONENT_OFFSET: Incomplete

def TTFOpenFile(fn): ...

class TTFontParser:
    ttfVersions: Incomplete
    ttcVersions: Incomplete
    fileKind: str
    validate: Incomplete
    subfontNameX: bytes
    def __init__(self, file, validate: int = 0, subfontIndex: int = 0) -> None: ...
    ttcVersion: Incomplete
    numSubfonts: Incomplete
    subfontOffsets: Incomplete
    def readTTCHeader(self) -> None: ...
    def getSubfont(self, subfontIndex) -> None: ...
    numTables: Incomplete
    searchRange: Incomplete
    entrySelector: Incomplete
    rangeShift: Incomplete
    table: Incomplete
    tables: Incomplete
    def readTableDirectory(self) -> None: ...
    version: Incomplete
    def readHeader(self): ...
    filename: Incomplete
    def readFile(self, f) -> None: ...
    def checksumTables(self) -> None: ...
    def checksumFile(self) -> None: ...
    def get_table_pos(self, tag): ...
    def seek(self, pos) -> None: ...
    def skip(self, delta) -> None: ...
    def seek_table(self, tag, offset_in_table: int = 0): ...
    def read_tag(self): ...
    def get_chunk(self, pos, length): ...
    def read_uint8(self): ...
    def read_ushort(self): ...
    def read_ulong(self): ...
    def read_short(self): ...
    def get_ushort(self, pos): ...
    def get_ulong(self, pos): ...
    def get_table(self, tag): ...

class TTFontMaker:
    tables: Incomplete
    def __init__(self) -> None: ...
    def add(self, tag, data) -> None: ...
    def makeStream(self): ...

class CMapFmt2SubHeader(NamedTuple):
    firstCode: Incomplete
    entryCount: Incomplete
    idDelta: Incomplete
    idRangeOffset: Incomplete

class TTFNameBytes(bytes):
    ustr: Incomplete
    def __new__(cls, b, enc: str = "utf8"): ...

class TTFontFile(TTFontParser):
    def __init__(self, file, charInfo: int = 1, validate: int = 0, subfontIndex: int = 0) -> None: ...
    name: Incomplete
    familyName: Incomplete
    styleName: Incomplete
    fullName: Incomplete
    uniqueFontID: Incomplete
    fontRevision: Incomplete
    unitsPerEm: Incomplete
    bbox: Incomplete
    ascent: Incomplete
    descent: Incomplete
    capHeight: Incomplete
    stemV: Incomplete
    italicAngle: Incomplete
    underlinePosition: Incomplete
    underlineThickness: Incomplete
    flags: Incomplete
    numGlyphs: Incomplete
    charToGlyph: Incomplete
    defaultWidth: Incomplete
    charWidths: Incomplete
    hmetrics: Incomplete
    glyphPos: Incomplete
    def extractInfo(self, charInfo: int = 1): ...
    def makeSubset(self, subset): ...

FF_FIXED: Incomplete
FF_SERIF: Incomplete
FF_SYMBOLIC: Incomplete
FF_SCRIPT: Incomplete
FF_NONSYMBOLIC: Incomplete
FF_ITALIC: Incomplete
FF_ALLCAP: Incomplete
FF_SMALLCAP: Incomplete
FF_FORCEBOLD: Incomplete

class TTFontFace(TTFontFile, pdfmetrics.TypeFace):
    def __init__(self, filename, validate: int = 0, subfontIndex: int = 0) -> None: ...
    def getCharWidth(self, code): ...
    def addSubsetObjects(self, doc, fontname, subset): ...

class TTEncoding:
    name: str
    def __init__(self) -> None: ...

class TTFont:
    class State:
        namePrefix: str
        nextCode: int
        internalName: Incomplete
        frozen: int
        subsets: Incomplete
        def __init__(self, asciiReadable: Incomplete | None = None, ttf: Incomplete | None = None) -> None: ...

    fontName: Incomplete
    face: Incomplete
    encoding: Incomplete
    state: Incomplete
    def __init__(
        self, name, filename, validate: int = 0, subfontIndex: int = 0, asciiReadable: Incomplete | None = None
    ) -> None: ...
    def stringWidth(self, text, size, encoding: str = "utf8"): ...
    def splitString(self, text, doc, encoding: str = "utf-8"): ...
    def getSubsetInternalName(self, subset, doc): ...
    def addObjects(self, doc) -> None: ...
