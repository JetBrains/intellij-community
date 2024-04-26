from pathlib import Path

from .enums import Align as Align, TextMode as TextMode, XPos as XPos, YPos as YPos
from .fpdf import FPDF as FPDF, FPDFException as FPDFException, TitleStyle as TitleStyle
from .html import HTML2FPDF as HTML2FPDF, HTMLMixin as HTMLMixin
from .prefs import ViewerPreferences as ViewerPreferences
from .template import FlexTemplate as FlexTemplate, Template as Template

__license__: str
__version__: str
FPDF_VERSION: str
FPDF_FONT_DIR: Path

__all__ = [
    "__version__",
    "__license__",
    "FPDF",
    "FPDFException",
    "Align",
    "XPos",
    "YPos",
    "Template",
    "FlexTemplate",
    "TitleStyle",
    "ViewerPreferences",
    "HTMLMixin",
    "HTML2FPDF",
    "FPDF_VERSION",
    "FPDF_FONT_DIR",
]
