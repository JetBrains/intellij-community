from _typeshed import Incomplete
from typing import Final

__version__: Final[str]

def asNative(s): ...

EXTENSIONS: Incomplete
FF_FIXED: Incomplete
FF_SERIF: Incomplete
FF_SYMBOLIC: Incomplete
FF_SCRIPT: Incomplete
FF_NONSYMBOLIC: Incomplete
FF_ITALIC: Incomplete
FF_ALLCAP: Incomplete
FF_SMALLCAP: Incomplete
FF_FORCEBOLD: Incomplete

class FontDescriptor:
    name: Incomplete
    fullName: Incomplete
    familyName: Incomplete
    styleName: Incomplete
    isBold: bool
    isItalic: bool
    isFixedPitch: bool
    isSymbolic: bool
    typeCode: Incomplete
    fileName: Incomplete
    metricsFileName: Incomplete
    timeModified: int
    def __init__(self) -> None: ...
    def getTag(self): ...

class FontFinder:
    useCache: Incomplete
    validate: Incomplete
    verbose: Incomplete
    def __init__(
        self, dirs=[], useCache: bool = True, validate: bool = False, recur: bool = False, fsEncoding=None, verbose: int = 0
    ) -> None: ...
    def addDirectory(self, dirName, recur=None) -> None: ...
    def addDirectories(self, dirNames, recur=None) -> None: ...
    def getFamilyNames(self): ...
    def getFontsInFamily(self, familyName): ...
    def getFamilyXmlReport(self): ...
    def getFontsWithAttributes(self, **kwds): ...
    def getFont(self, familyName, bold: bool = False, italic: bool = False): ...
    def save(self, fileName) -> None: ...
    def load(self, fileName) -> None: ...
    def search(self) -> None: ...

def test() -> None: ...
