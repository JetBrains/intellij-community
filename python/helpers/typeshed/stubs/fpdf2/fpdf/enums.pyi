from _typeshed import Incomplete, Self
from enum import Enum, Flag, IntEnum

from .syntax import Name

class DocumentState(IntEnum):
    UNINITIALIZED: int
    READY: int
    GENERATING_PAGE: int
    CLOSED: int

class CoerciveEnum(Enum):
    @classmethod
    def coerce(cls: type[Self], value: Self | str) -> Self: ...

class CoerciveIntEnum(IntEnum):
    @classmethod
    def coerce(cls: type[Self], value: Self | str | int) -> Self: ...

class CharVPos(CoerciveEnum):
    SUP: str
    SUB: str
    NOM: str
    DENOM: str
    LINE: str

class Align(CoerciveEnum):
    C: str
    X: str
    L: str
    R: str
    J: str

class RenderStyle(CoerciveEnum):
    D: str
    F: str
    DF: str
    @property
    def operator(self) -> str: ...
    @property
    def is_draw(self) -> bool: ...
    @property
    def is_fill(self) -> bool: ...

class TextMode(CoerciveIntEnum):
    FILL: int
    STROKE: int
    FILL_STROKE: int
    INVISIBLE: int
    FILL_CLIP: int
    STROKE_CLIP: int
    FILL_STROKE_CLIP: int
    CLIP: int

class XPos(CoerciveEnum):
    LEFT: str
    RIGHT: str
    START: str
    END: str
    WCONT: str
    CENTER: str
    LMARGIN: str
    RMARGIN: str

class YPos(CoerciveEnum):
    TOP: str
    LAST: str
    NEXT: str
    TMARGIN: str
    BMARGIN: str

class PageLayout(CoerciveEnum):
    SINGLE_PAGE: Name
    ONE_COLUMN: Name
    TWO_COLUMN_LEFT: Name
    TWO_COLUMN_RIGHT: Name
    TWO_PAGE_LEFT: Name
    TWO_PAGE_RIGHT: Name

class PageMode(CoerciveEnum):
    USE_NONE: Name
    USE_OUTLINES: Name
    USE_THUMBS: Name
    FULL_SCREEN: Name
    USE_OC: Name
    USE_ATTACHMENTS: Name

class TextMarkupType(CoerciveEnum):
    HIGHLIGHT: Name
    UNDERLINE: Name
    SQUIGGLY: Name
    STRIKE_OUT: Name

class BlendMode(CoerciveEnum):
    NORMAL: Name
    MULTIPLY: Name
    SCREEN: Name
    OVERLAY: Name
    DARKEN: Name
    LIGHTEN: Name
    COLOR_DODGE: Name
    COLOR_BURN: Name
    HARD_LIGHT: Name
    SOFT_LIGHT: Name
    DIFFERENCE: Name
    EXCLUSION: Name
    HUE: Name
    SATURATION: Name
    COLOR: Name
    LUMINOSITY: Name

class AnnotationFlag(CoerciveIntEnum):
    INVISIBLE: int
    HIDDEN: int
    PRINT: int
    NO_ZOOM: int
    NO_ROTATE: int
    NO_VIEW: int
    READ_ONLY: int
    LOCKED: int
    TOGGLE_NO_VIEW: int
    LOCKED_CONTENTS: int

class AnnotationName(CoerciveEnum):
    NOTE: Name
    COMMENT: Name
    HELP: Name
    PARAGRAPH: Name
    NEW_PARAGRAPH: Name
    INSERT: Name

class FileAttachmentAnnotationName(CoerciveEnum):
    PUSH_PIN: Name
    GRAPH_PUSH_PIN: Name
    PAPERCLIP_TAG: Name

class IntersectionRule(CoerciveEnum):
    NONZERO: str
    EVENODD: str

class PathPaintRule(CoerciveEnum):
    STROKE: str
    FILL_NONZERO: str
    FILL_EVENODD: str
    STROKE_FILL_NONZERO: str
    STROKE_FILL_EVENODD: str
    DONT_PAINT: str
    AUTO: str

class ClippingPathIntersectionRule(CoerciveEnum):
    NONZERO: str
    EVENODD: str

class StrokeCapStyle(CoerciveIntEnum):
    BUTT: int
    ROUND: int
    SQUARE: int

class StrokeJoinStyle(CoerciveIntEnum):
    MITER: int
    ROUND: int
    BEVEL: int

class PDFStyleKeys(Enum):
    FILL_ALPHA: Name
    BLEND_MODE: Name
    STROKE_ALPHA: Name
    STROKE_ADJUSTMENT: Name
    STROKE_WIDTH: Name
    STROKE_CAP_STYLE: Name
    STROKE_JOIN_STYLE: Name
    STROKE_MITER_LIMIT: Name
    STROKE_DASH_PATTERN: Name

class Corner(CoerciveEnum):
    TOP_RIGHT: str
    TOP_LEFT: str
    BOTTOM_RIGHT: str
    BOTTOM_LEFT: str

class FontDescriptorFlags(Flag):
    FIXED_PITCH: int
    SYMBOLIC: int
    ITALIC: int
    FORCE_BOLD: int

__pdoc__: Incomplete
