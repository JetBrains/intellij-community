from _typeshed import Unused
from typing import ClassVar
from typing_extensions import Literal

from openpyxl.descriptors.base import Typed, _ConvertibleToInt
from openpyxl.descriptors.nested import NestedText
from openpyxl.descriptors.serialisable import Serialisable

def get_version(): ...

class DigSigBlob(Serialisable):
    __elements__: ClassVar[tuple[str, ...]]
    __attrs__: ClassVar[tuple[str, ...]]

class VectorLpstr(Serialisable):
    __elements__: ClassVar[tuple[str, ...]]
    __attrs__: ClassVar[tuple[str, ...]]

class VectorVariant(Serialisable):
    __elements__: ClassVar[tuple[str, ...]]
    __attrs__: ClassVar[tuple[str, ...]]

class ExtendedProperties(Serialisable):
    tagname: ClassVar[str]
    Template: NestedText[str, Literal[True]]
    Manager: NestedText[str, Literal[True]]
    Company: NestedText[str, Literal[True]]
    Pages: NestedText[int, Literal[True]]
    Words: NestedText[int, Literal[True]]
    Characters: NestedText[int, Literal[True]]
    PresentationFormat: NestedText[str, Literal[True]]
    Lines: NestedText[int, Literal[True]]
    Paragraphs: NestedText[int, Literal[True]]
    Slides: NestedText[int, Literal[True]]
    Notes: NestedText[int, Literal[True]]
    TotalTime: NestedText[int, Literal[True]]
    HiddenSlides: NestedText[int, Literal[True]]
    MMClips: NestedText[int, Literal[True]]
    ScaleCrop: NestedText[str, Literal[True]]
    HeadingPairs: Typed[VectorVariant, Literal[True]]
    TitlesOfParts: Typed[VectorLpstr, Literal[True]]
    LinksUpToDate: NestedText[str, Literal[True]]
    CharactersWithSpaces: NestedText[int, Literal[True]]
    SharedDoc: NestedText[str, Literal[True]]
    HyperlinkBase: NestedText[str, Literal[True]]
    HLinks: Typed[VectorVariant, Literal[True]]
    HyperlinksChanged: NestedText[str, Literal[True]]
    DigSig: Typed[DigSigBlob, Literal[True]]
    Application: NestedText[str, Literal[True]]
    AppVersion: NestedText[str, Literal[True]]
    DocSecurity: NestedText[int, Literal[True]]
    __elements__: ClassVar[tuple[str, ...]]
    def __init__(
        self,
        Template: object = None,
        Manager: object = None,
        Company: object = None,
        Pages: _ConvertibleToInt | None = None,
        Words: _ConvertibleToInt | None = None,
        Characters: _ConvertibleToInt | None = None,
        PresentationFormat: object = None,
        Lines: _ConvertibleToInt | None = None,
        Paragraphs: _ConvertibleToInt | None = None,
        Slides: _ConvertibleToInt | None = None,
        Notes: _ConvertibleToInt | None = None,
        TotalTime: _ConvertibleToInt | None = None,
        HiddenSlides: _ConvertibleToInt | None = None,
        MMClips: _ConvertibleToInt | None = None,
        ScaleCrop: object = None,
        HeadingPairs: Unused = None,
        TitlesOfParts: Unused = None,
        LinksUpToDate: object = None,
        CharactersWithSpaces: _ConvertibleToInt | None = None,
        SharedDoc: object = None,
        HyperlinkBase: object = None,
        HLinks: Unused = None,
        HyperlinksChanged: object = None,
        DigSig: Unused = None,
        Application: object = "Microsoft Excel",
        AppVersion: object = None,
        DocSecurity: _ConvertibleToInt | None = None,
    ) -> None: ...
    def to_tree(self): ...
