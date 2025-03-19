from enum import Enum, Flag, IntEnum, IntFlag
from typing import Literal
from typing_extensions import Self

from .syntax import Name

class SignatureFlag(IntEnum):
    SIGNATURES_EXIST = 1
    APPEND_ONLY = 2

class CoerciveEnum(Enum):
    @classmethod
    def coerce(cls, value: Self | str) -> Self: ...

class CoerciveIntEnum(IntEnum):
    @classmethod
    def coerce(cls, value: Self | str | int) -> Self: ...

class CoerciveIntFlag(IntFlag):
    @classmethod
    def coerce(cls, value: Self | str | int) -> Self: ...

class WrapMode(CoerciveEnum):
    WORD = "WORD"
    CHAR = "CHAR"

class CharVPos(CoerciveEnum):
    SUP = "SUP"
    SUB = "SUB"
    NOM = "NOM"
    DENOM = "DENOM"
    LINE = "LINE"

class Align(CoerciveEnum):
    C = "CENTER"
    X = "X_CENTER"
    L = "LEFT"
    R = "RIGHT"
    J = "JUSTIFY"

class VAlign(CoerciveEnum):
    M = "MIDDLE"
    T = "TOP"
    B = "BOTTOM"

class TextEmphasis(CoerciveIntFlag):
    NONE = 0
    B = 1
    I = 2
    U = 4

    @property
    def style(self) -> str: ...

class MethodReturnValue(CoerciveIntFlag):
    PAGE_BREAK = 1
    LINES = 2
    HEIGHT = 4

class TableBordersLayout(CoerciveEnum):
    ALL = "ALL"
    NONE = "NONE"
    INTERNAL = "INTERNAL"
    MINIMAL = "MINIMAL"
    HORIZONTAL_LINES = "HORIZONTAL_LINES"
    NO_HORIZONTAL_LINES = "NO_HORIZONTAL_LINES"
    SINGLE_TOP_LINE = "SINGLE_TOP_LINE"

class TableCellFillMode(CoerciveEnum):
    NONE = "NONE"
    ALL = "ALL"
    ROWS = "ROWS"
    COLUMNS = "COLUMNS"
    EVEN_ROWS = "EVEN_ROWS"
    EVEN_COLUMNS = "EVEN_COLUMNS"

    def should_fill_cell(self, i: int, j: int) -> bool: ...

class TableSpan(CoerciveEnum):
    ROW = "ROW"
    COL = "COL"

class TableHeadingsDisplay(CoerciveIntEnum):
    NONE = 0
    ON_TOP_OF_EVERY_PAGE = 1

class RenderStyle(CoerciveEnum):
    D = "DRAW"
    F = "FILL"
    DF = "DRAW_FILL"
    @property
    def operator(self) -> str: ...
    @property
    def is_draw(self) -> bool: ...
    @property
    def is_fill(self) -> bool: ...

class TextMode(CoerciveIntEnum):
    FILL = 0
    STROKE = 1
    FILL_STROKE = 2
    INVISIBLE = 3
    FILL_CLIP = 4
    STROKE_CLIP = 5
    FILL_STROKE_CLIP = 6
    CLIP = 7

class XPos(CoerciveEnum):
    LEFT = "LEFT"
    RIGHT = "RIGHT"
    START = "START"
    END = "END"
    WCONT = "WCONT"
    CENTER = "CENTER"
    LMARGIN = "LMARGIN"
    RMARGIN = "RMARGIN"

class YPos(CoerciveEnum):
    TOP = "TOP"
    LAST = "LAST"
    NEXT = "NEXT"
    TMARGIN = "TMARGIN"
    BMARGIN = "BMARGIN"

class Angle(CoerciveIntEnum):
    NORTH = 90
    EAST = 0
    SOUTH = 270
    WEST = 180
    NORTHEAST = 45
    SOUTHEAST = 315
    SOUTHWEST = 225
    NORTHWEST = 135

class PageLayout(CoerciveEnum):
    SINGLE_PAGE = Name("SinglePage")
    ONE_COLUMN = Name("OneColumn")
    TWO_COLUMN_LEFT = Name("TwoColumnLeft")
    TWO_COLUMN_RIGHT = Name("TwoColumnRight")
    TWO_PAGE_LEFT = Name("TwoPageLeft")
    TWO_PAGE_RIGHT = Name("TwoPageRight")

class PageMode(CoerciveEnum):
    USE_NONE = Name("UseNone")
    USE_OUTLINES = Name("UseOutlines")
    USE_THUMBS = Name("UseThumbs")
    FULL_SCREEN = Name("FullScreen")
    USE_OC = Name("UseOC")
    USE_ATTACHMENTS = Name("UseAttachments")

class TextMarkupType(CoerciveEnum):
    HIGHLIGHT = Name("Highlight")
    UNDERLINE = Name("Underline")
    SQUIGGLY = Name("Squiggly")
    STRIKE_OUT = Name("StrikeOut")

class BlendMode(CoerciveEnum):
    NORMAL = Name("Normal")
    MULTIPLY = Name("Multiply")
    SCREEN = Name("Screen")
    OVERLAY = Name("Overlay")
    DARKEN = Name("Darken")
    LIGHTEN = Name("Lighten")
    COLOR_DODGE = Name("ColorDodge")
    COLOR_BURN = Name("ColorBurn")
    HARD_LIGHT = Name("HardLight")
    SOFT_LIGHT = Name("SoftLight")
    DIFFERENCE = Name("Difference")
    EXCLUSION = Name("Exclusion")
    HUE = Name("Hue")
    SATURATION = Name("Saturation")
    COLOR = Name("Color")
    LUMINOSITY = Name("Luminosity")

class AnnotationFlag(CoerciveIntEnum):
    INVISIBLE = 1
    HIDDEN = 2
    PRINT = 4
    NO_ZOOM = 8
    NO_ROTATE = 16
    NO_VIEW = 32
    READ_ONLY = 64
    LOCKED = 128
    TOGGLE_NO_VIEW = 256
    LOCKED_CONTENTS = 512

class AnnotationName(CoerciveEnum):
    NOTE = Name("Note")
    COMMENT = Name("Comment")
    HELP = Name("Help")
    PARAGRAPH = Name("Paragraph")
    NEW_PARAGRAPH = Name("NewParagraph")
    INSERT = Name("Insert")

class FileAttachmentAnnotationName(CoerciveEnum):
    PUSH_PIN = Name("PushPin")
    GRAPH_PUSH_PIN = Name("GraphPushPin")
    PAPERCLIP_TAG = Name("PaperclipTag")

class IntersectionRule(CoerciveEnum):
    NONZERO = "nonzero"
    EVENODD = "evenodd"

class PathPaintRule(CoerciveEnum):
    STROKE = "S"
    FILL_NONZERO = "f"
    FILL_EVENODD = "f*"
    STROKE_FILL_NONZERO = "B"
    STROKE_FILL_EVENODD = "B*"
    DONT_PAINT = "n"
    AUTO = "auto"

class ClippingPathIntersectionRule(CoerciveEnum):
    NONZERO = "W"
    EVENODD = "W*"

class StrokeCapStyle(CoerciveIntEnum):
    BUTT = 0
    ROUND = 1
    SQUARE = 2

class StrokeJoinStyle(CoerciveIntEnum):
    MITER = 0
    ROUND = 1
    BEVEL = 2

class PDFStyleKeys(Enum):
    FILL_ALPHA = Name("ca")
    BLEND_MODE = Name("BM")
    STROKE_ALPHA = Name("CA")
    STROKE_ADJUSTMENT = Name("SA")
    STROKE_WIDTH = Name("LW")
    STROKE_CAP_STYLE = Name("LC")
    STROKE_JOIN_STYLE = Name("LJ")
    STROKE_MITER_LIMIT = Name("ML")
    STROKE_DASH_PATTERN = Name("D")

class Corner(CoerciveEnum):
    TOP_RIGHT = "TOP_RIGHT"
    TOP_LEFT = "TOP_LEFT"
    BOTTOM_RIGHT = "BOTTOM_RIGHT"
    BOTTOM_LEFT = "BOTTOM_LEFT"

class FontDescriptorFlags(Flag):
    FIXED_PITCH = 1
    SYMBOLIC = 4
    ITALIC = 64
    FORCE_BOLD = 262144

class AccessPermission(IntFlag):
    PRINT_LOW_RES = 4
    MODIFY = 8
    COPY = 16
    ANNOTATION = 32
    FILL_FORMS = 256
    COPY_FOR_ACCESSIBILITY = 512
    ASSEMBLE = 1024
    PRINT_HIGH_RES = 2048
    @classmethod
    def all(cls) -> int: ...
    @classmethod
    def none(cls) -> Literal[0]: ...

class EncryptionMethod(Enum):
    NO_ENCRYPTION = 0
    RC4 = 1
    AES_128 = 2
    AES_256 = 3

class TextDirection(CoerciveEnum):
    LTR = "LTR"
    RTL = "RTL"
    TTB = "TTB"
    BTT = "BTT"
