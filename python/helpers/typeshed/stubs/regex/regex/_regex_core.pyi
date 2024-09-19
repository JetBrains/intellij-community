import enum
from collections.abc import Callable
from typing import Any, AnyStr, Generic
from typing_extensions import TypeAlias

from .regex import Pattern

class error(Exception):
    def __init__(self, message: str, pattern: AnyStr | None = None, pos: int | None = None) -> None: ...

class RegexFlag(enum.IntFlag):
    A = 0x80
    ASCII = A
    B = 0x1000
    BESTMATCH = B
    D = 0x200
    DEBUG = D
    E = 0x8000
    ENHANCEMATCH = E
    F = 0x4000
    FULLCASE = F
    I = 0x2
    IGNORECASE = I
    L = 0x4
    LOCALE = L
    M = 0x8
    MULTILINE = M
    P = 0x10000
    POSIX = P
    R = 0x400
    REVERSE = R
    T = 0x1
    TEMPLATE = T
    S = 0x10
    DOTALL = S
    U = 0x20
    UNICODE = U
    V0 = 0x2000
    VERSION0 = V0
    V1 = 0x100
    VERSION1 = V1
    W = 0x800
    WORD = W
    X = 0x40
    VERBOSE = X

A = RegexFlag.A
ASCII = RegexFlag.ASCII
B = RegexFlag.B
BESTMATCH = RegexFlag.BESTMATCH
D = RegexFlag.D
DEBUG = RegexFlag.DEBUG
E = RegexFlag.E
ENHANCEMATCH = RegexFlag.ENHANCEMATCH
F = RegexFlag.F
FULLCASE = RegexFlag.FULLCASE
I = RegexFlag.I
IGNORECASE = RegexFlag.IGNORECASE
L = RegexFlag.L
LOCALE = RegexFlag.LOCALE
M = RegexFlag.M
MULTILINE = RegexFlag.MULTILINE
P = RegexFlag.P
POSIX = RegexFlag.POSIX
R = RegexFlag.R
REVERSE = RegexFlag.REVERSE
T = RegexFlag.T
TEMPLATE = RegexFlag.TEMPLATE
S = RegexFlag.S
DOTALL = RegexFlag.DOTALL
U = RegexFlag.U
UNICODE = RegexFlag.UNICODE
V0 = RegexFlag.V0
VERSION0 = RegexFlag.VERSION0
V1 = RegexFlag.V1
VERSION1 = RegexFlag.VERSION1
W = RegexFlag.W
WORD = RegexFlag.WORD
X = RegexFlag.X
VERBOSE = RegexFlag.VERBOSE

DEFAULT_VERSION: RegexFlag

_Lexicon: TypeAlias = list[tuple[AnyStr, Callable[[Scanner[AnyStr], AnyStr], Any]]]

class Scanner(Generic[AnyStr]):
    lexicon: _Lexicon[AnyStr]
    scanner: Pattern[AnyStr]

    def __init__(self, lexicon: _Lexicon[AnyStr], flags: int = 0) -> None: ...
    def scan(self, string: AnyStr) -> tuple[list[Any], AnyStr]: ...
