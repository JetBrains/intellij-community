from _typeshed import Incomplete

RequirePyRXP: int
simpleparse: int

class _smartDecode:
    @staticmethod
    def __call__(s): ...

smartDecode: _smartDecode
NONAME: str
NAMEKEY: int
CONTENTSKEY: int
CDATAMARKER: str
LENCDATAMARKER: Incomplete
CDATAENDMARKER: str
replacelist: Incomplete

def unEscapeContentList(contentList): ...
def parsexmlSimple(xmltext, oneOutermostTag: int = 0, eoCB: Incomplete | None = None, entityReplacer=...): ...

parsexml = parsexmlSimple

def parseFile(filename): ...

verbose: int

def skip_prologue(text, cursor): ...
def parsexml0(xmltext, startingat: int = 0, toplevel: int = 1, entityReplacer=...): ...
def pprettyprint(parsedxml): ...
def testparse(s, dump: int = 0) -> None: ...
def test(dump: int = 0) -> None: ...
