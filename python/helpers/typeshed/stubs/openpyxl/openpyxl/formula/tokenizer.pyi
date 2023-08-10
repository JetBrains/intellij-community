from typing import Any

class TokenizerError(Exception): ...

class Tokenizer:
    SN_RE: Any
    WSPACE_RE: Any
    STRING_REGEXES: Any
    ERROR_CODES: Any
    TOKEN_ENDERS: str
    formula: Any
    items: Any
    token_stack: Any
    offset: int
    token: Any
    def __init__(self, formula) -> None: ...
    def check_scientific_notation(self): ...
    def assert_empty_token(self, can_follow=...) -> None: ...
    def save_token(self) -> None: ...
    def render(self): ...

class Token:
    LITERAL: str
    OPERAND: str
    FUNC: str
    ARRAY: str
    PAREN: str
    SEP: str
    OP_PRE: str
    OP_IN: str
    OP_POST: str
    WSPACE: str
    value: Any
    type: Any
    subtype: Any
    def __init__(self, value, type_, subtype: str = ...) -> None: ...
    TEXT: str
    NUMBER: str
    LOGICAL: str
    ERROR: str
    RANGE: str
    @classmethod
    def make_operand(cls, value): ...
    OPEN: str
    CLOSE: str
    @classmethod
    def make_subexp(cls, value, func: bool = ...): ...
    def get_closer(self): ...
    ARG: str
    ROW: str
    @classmethod
    def make_separator(cls, value): ...
