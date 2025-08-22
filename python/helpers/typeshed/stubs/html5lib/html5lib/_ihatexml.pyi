import re
from _typeshed import Incomplete
from collections.abc import Iterable

baseChar: str
ideographic: str
combiningCharacter: str
digit: str
extender: str
letter: str
name: str
nameFirst: str
reChar: re.Pattern[str]
reCharRange: re.Pattern[str]

def charStringToList(chars: str) -> list[str]: ...
def normaliseCharList(charList: Iterable[str]) -> list[str]: ...

max_unicode: int

def missingRanges(charList: Iterable[str]) -> list[str]: ...
def listToRegexpStr(charList): ...
def hexToInt(hex_str: str | bytes | bytearray) -> int: ...
def escapeRegexp(string: str) -> str: ...

nonXmlNameBMPRegexp: re.Pattern[str]
nonXmlNameFirstBMPRegexp: re.Pattern[str]
nonPubidCharRegexp: re.Pattern[str]

class InfosetFilter:
    replacementRegexp: re.Pattern[str]
    dropXmlnsLocalName: Incomplete
    dropXmlnsAttrNs: Incomplete
    preventDoubleDashComments: Incomplete
    preventDashAtCommentEnd: Incomplete
    replaceFormFeedCharacters: Incomplete
    preventSingleQuotePubid: Incomplete
    replaceCache: Incomplete
    def __init__(
        self,
        dropXmlnsLocalName: bool = False,
        dropXmlnsAttrNs: bool = False,
        preventDoubleDashComments: bool = False,
        preventDashAtCommentEnd: bool = False,
        replaceFormFeedCharacters: bool = True,
        preventSingleQuotePubid: bool = False,
    ) -> None: ...
    def coerceAttribute(self, name, namespace=None): ...
    def coerceElement(self, name): ...
    def coerceComment(self, data): ...
    def coerceCharacters(self, data): ...
    def coercePubid(self, data): ...
    def toXmlName(self, name): ...
    def getReplacementCharacter(self, char): ...
    def fromXmlName(self, name): ...
    def escapeChar(self, char): ...
    def unescapeChar(self, charcode): ...
