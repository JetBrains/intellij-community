import sys
from typing import Dict

ENDMARKER = ...  # type: int
NAME = ...  # type: int
NUMBER = ...  # type: int
STRING = ...  # type: int
NEWLINE = ...  # type: int
INDENT = ...  # type: int
DEDENT = ...  # type: int
LPAR = ...  # type: int
RPAR = ...  # type: int
LSQB = ...  # type: int
RSQB = ...  # type: int
COLON = ...  # type: int
COMMA = ...  # type: int
SEMI = ...  # type: int
PLUS = ...  # type: int
MINUS = ...  # type: int
STAR = ...  # type: int
SLASH = ...  # type: int
VBAR = ...  # type: int
AMPER = ...  # type: int
LESS = ...  # type: int
GREATER = ...  # type: int
EQUAL = ...  # type: int
DOT = ...  # type: int
PERCENT = ...  # type: int
if sys.version_info < (3,):
    BACKQUOTE = ...  # type: int
LBRACE = ...  # type: int
RBRACE = ...  # type: int
EQEQUAL = ...  # type: int
NOTEQUAL = ...  # type: int
LESSEQUAL = ...  # type: int
GREATEREQUAL = ...  # type: int
TILDE = ...  # type: int
CIRCUMFLEX = ...  # type: int
LEFTSHIFT = ...  # type: int
RIGHTSHIFT = ...  # type: int
DOUBLESTAR = ...  # type: int
PLUSEQUAL = ...  # type: int
MINEQUAL = ...  # type: int
STAREQUAL = ...  # type: int
SLASHEQUAL = ...  # type: int
PERCENTEQUAL = ...  # type: int
AMPEREQUAL = ...  # type: int
VBAREQUAL = ...  # type: int
CIRCUMFLEXEQUAL = ...  # type: int
LEFTSHIFTEQUAL = ...  # type: int
RIGHTSHIFTEQUAL = ...  # type: int
DOUBLESTAREQUAL = ...  # type: int
DOUBLESLASH = ...  # type: int
DOUBLESLASHEQUAL = ...  # type: int
AT = ...  # type: int
if sys.version_info >= (3,):
    RARROW = ...  # type: int
    ELLIPSIS = ...  # type: int
if sys.version_info >= (3, 5):
    ATEQUAL = ...  # type: int
    AWAIT = ...  # type: int
    ASYNC = ...  # type: int
OP = ...  # type: int
ERRORTOKEN = ...  # type: int
N_TOKENS = ...  # type: int
NT_OFFSET = ...  # type: int
tok_name = ...  # type: Dict[int, str]

def ISTERMINAL(x: int) -> bool: ...
def ISNONTERMINAL(x: int) -> bool: ...
def ISEOF(x: int) -> bool: ...
