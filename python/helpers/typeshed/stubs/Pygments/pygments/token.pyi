from collections.abc import Mapping
from typing import Tuple

class _TokenType(Tuple[str]):  # TODO: change to lower-case tuple once new mypy released
    parent: _TokenType | None
    def split(self) -> list[_TokenType]: ...
    subtypes: set[_TokenType]
    def __init__(self, *args: str) -> None: ...
    def __contains__(self, val: _TokenType) -> bool: ...  # type: ignore
    def __getattr__(self, name: str) -> _TokenType: ...
    def __copy__(self): ...
    def __deepcopy__(self, memo): ...

Token: _TokenType
Text: _TokenType
Whitespace: _TokenType
Escape: _TokenType
Error: _TokenType
Other: _TokenType
Keyword: _TokenType
Name: _TokenType
Literal: _TokenType
String: _TokenType
Number: _TokenType
Punctuation: _TokenType
Operator: _TokenType
Comment: _TokenType
Generic: _TokenType

def is_token_subtype(ttype, other): ...
def string_to_tokentype(s): ...

# dict, but shouldn't be mutated
STANDARD_TYPES: Mapping[_TokenType, str]
