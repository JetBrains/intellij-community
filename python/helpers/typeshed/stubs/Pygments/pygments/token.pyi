from _typeshed import Self
from collections.abc import Mapping
from typing import Any

class _TokenType(tuple[str, ...]):
    parent: _TokenType | None
    def split(self) -> list[_TokenType]: ...
    subtypes: set[_TokenType]
    def __contains__(self, val: _TokenType) -> bool: ...  # type: ignore[override]
    def __getattr__(self, name: str) -> _TokenType: ...
    def __copy__(self: Self) -> Self: ...
    def __deepcopy__(self: Self, memo: Any) -> Self: ...

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
