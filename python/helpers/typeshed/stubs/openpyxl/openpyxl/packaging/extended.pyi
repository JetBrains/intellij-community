from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

def get_version(): ...

class DigSigBlob(Serialisable):  # type: ignore[misc]
    __elements__: Any
    __attrs__: Any

class VectorLpstr(Serialisable):  # type: ignore[misc]
    __elements__: Any
    __attrs__: Any

class VectorVariant(Serialisable):  # type: ignore[misc]
    __elements__: Any
    __attrs__: Any

class ExtendedProperties(Serialisable):
    tagname: str
    Template: Any
    Manager: Any
    Company: Any
    Pages: Any
    Words: Any
    Characters: Any
    PresentationFormat: Any
    Lines: Any
    Paragraphs: Any
    Slides: Any
    Notes: Any
    TotalTime: Any
    HiddenSlides: Any
    MMClips: Any
    ScaleCrop: Any
    HeadingPairs: Any
    TitlesOfParts: Any
    LinksUpToDate: Any
    CharactersWithSpaces: Any
    SharedDoc: Any
    HyperlinkBase: Any
    HLinks: Any
    HyperlinksChanged: Any
    DigSig: Any
    Application: Any
    AppVersion: Any
    DocSecurity: Any
    __elements__: Any
    def __init__(
        self,
        Template: Any | None = ...,
        Manager: Any | None = ...,
        Company: Any | None = ...,
        Pages: Any | None = ...,
        Words: Any | None = ...,
        Characters: Any | None = ...,
        PresentationFormat: Any | None = ...,
        Lines: Any | None = ...,
        Paragraphs: Any | None = ...,
        Slides: Any | None = ...,
        Notes: Any | None = ...,
        TotalTime: Any | None = ...,
        HiddenSlides: Any | None = ...,
        MMClips: Any | None = ...,
        ScaleCrop: Any | None = ...,
        HeadingPairs: Any | None = ...,
        TitlesOfParts: Any | None = ...,
        LinksUpToDate: Any | None = ...,
        CharactersWithSpaces: Any | None = ...,
        SharedDoc: Any | None = ...,
        HyperlinkBase: Any | None = ...,
        HLinks: Any | None = ...,
        HyperlinksChanged: Any | None = ...,
        DigSig: Any | None = ...,
        Application: str = ...,
        AppVersion: Any | None = ...,
        DocSecurity: Any | None = ...,
    ) -> None: ...
    def to_tree(self): ...
