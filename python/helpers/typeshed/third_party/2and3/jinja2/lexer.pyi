from typing import Any, Optional

whitespace_re = ...  # type: Any
string_re = ...  # type: Any
integer_re = ...  # type: Any
name_re = ...  # type: Any
float_re = ...  # type: Any
newline_re = ...  # type: Any
TOKEN_ADD = ...  # type: Any
TOKEN_ASSIGN = ...  # type: Any
TOKEN_COLON = ...  # type: Any
TOKEN_COMMA = ...  # type: Any
TOKEN_DIV = ...  # type: Any
TOKEN_DOT = ...  # type: Any
TOKEN_EQ = ...  # type: Any
TOKEN_FLOORDIV = ...  # type: Any
TOKEN_GT = ...  # type: Any
TOKEN_GTEQ = ...  # type: Any
TOKEN_LBRACE = ...  # type: Any
TOKEN_LBRACKET = ...  # type: Any
TOKEN_LPAREN = ...  # type: Any
TOKEN_LT = ...  # type: Any
TOKEN_LTEQ = ...  # type: Any
TOKEN_MOD = ...  # type: Any
TOKEN_MUL = ...  # type: Any
TOKEN_NE = ...  # type: Any
TOKEN_PIPE = ...  # type: Any
TOKEN_POW = ...  # type: Any
TOKEN_RBRACE = ...  # type: Any
TOKEN_RBRACKET = ...  # type: Any
TOKEN_RPAREN = ...  # type: Any
TOKEN_SEMICOLON = ...  # type: Any
TOKEN_SUB = ...  # type: Any
TOKEN_TILDE = ...  # type: Any
TOKEN_WHITESPACE = ...  # type: Any
TOKEN_FLOAT = ...  # type: Any
TOKEN_INTEGER = ...  # type: Any
TOKEN_NAME = ...  # type: Any
TOKEN_STRING = ...  # type: Any
TOKEN_OPERATOR = ...  # type: Any
TOKEN_BLOCK_BEGIN = ...  # type: Any
TOKEN_BLOCK_END = ...  # type: Any
TOKEN_VARIABLE_BEGIN = ...  # type: Any
TOKEN_VARIABLE_END = ...  # type: Any
TOKEN_RAW_BEGIN = ...  # type: Any
TOKEN_RAW_END = ...  # type: Any
TOKEN_COMMENT_BEGIN = ...  # type: Any
TOKEN_COMMENT_END = ...  # type: Any
TOKEN_COMMENT = ...  # type: Any
TOKEN_LINESTATEMENT_BEGIN = ...  # type: Any
TOKEN_LINESTATEMENT_END = ...  # type: Any
TOKEN_LINECOMMENT_BEGIN = ...  # type: Any
TOKEN_LINECOMMENT_END = ...  # type: Any
TOKEN_LINECOMMENT = ...  # type: Any
TOKEN_DATA = ...  # type: Any
TOKEN_INITIAL = ...  # type: Any
TOKEN_EOF = ...  # type: Any
operators = ...  # type: Any
reverse_operators = ...  # type: Any
operator_re = ...  # type: Any
ignored_tokens = ...  # type: Any
ignore_if_empty = ...  # type: Any

def describe_token(token): ...
def describe_token_expr(expr): ...
def count_newlines(value): ...
def compile_rules(environment): ...

class Failure:
    message = ...  # type: Any
    error_class = ...  # type: Any
    def __init__(self, message, cls: Any = ...) -> None: ...
    def __call__(self, lineno, filename): ...

class Token(tuple):
    lineno = ...  # type: Any
    type = ...  # type: Any
    value = ...  # type: Any
    def __new__(cls, lineno, type, value): ...
    def test(self, expr): ...
    def test_any(self, *iterable): ...

class TokenStreamIterator:
    stream = ...  # type: Any
    def __init__(self, stream) -> None: ...
    def __iter__(self): ...
    def __next__(self): ...

class TokenStream:
    name = ...  # type: Any
    filename = ...  # type: Any
    closed = ...  # type: bool
    current = ...  # type: Any
    def __init__(self, generator, name, filename) -> None: ...
    def __iter__(self): ...
    def __bool__(self): ...
    __nonzero__ = ...  # type: Any
    eos = ...  # type: Any
    def push(self, token): ...
    def look(self): ...
    def skip(self, n: int = ...): ...
    def next_if(self, expr): ...
    def skip_if(self, expr): ...
    def __next__(self): ...
    def close(self): ...
    def expect(self, expr): ...

def get_lexer(environment): ...

class Lexer:
    newline_sequence = ...  # type: Any
    keep_trailing_newline = ...  # type: Any
    rules = ...  # type: Any
    def __init__(self, environment) -> None: ...
    def tokenize(self, source, name: Optional[Any] = ..., filename: Optional[Any] = ..., state: Optional[Any] = ...): ...
    def wrap(self, stream, name: Optional[Any] = ..., filename: Optional[Any] = ...): ...
    def tokeniter(self, source, name, filename: Optional[Any] = ..., state: Optional[Any] = ...): ...
