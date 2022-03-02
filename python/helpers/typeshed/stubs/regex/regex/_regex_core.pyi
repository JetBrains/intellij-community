from typing import AnyStr

class error(Exception):
    def __init__(self, message: str, pattern: AnyStr | None = ..., pos: int | None = ...) -> None: ...

A: int
ASCII: int
B: int
BESTMATCH: int
D: int
DEBUG: int
E: int
ENHANCEMATCH: int
F: int
FULLCASE: int
I: int
IGNORECASE: int
L: int
LOCALE: int
M: int
MULTILINE: int
P: int
POSIX: int
R: int
REVERSE: int
T: int
TEMPLATE: int
S: int
DOTALL: int
U: int
UNICODE: int
V0: int
VERSION0: int
V1: int
VERSION1: int
W: int
WORD: int
X: int
VERBOSE: int

DEFAULT_VERSION: int
