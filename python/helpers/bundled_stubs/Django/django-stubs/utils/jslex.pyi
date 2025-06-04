from collections.abc import Iterator
from typing import Any

class Tok:
    num: int
    id: int
    name: str
    regex: str
    next: str | None
    def __init__(self, name: str, regex: str, next: str | None = None) -> None: ...

def literals(choices: str, prefix: str = "", suffix: str = "") -> str: ...

class Lexer:
    regexes: Any
    toks: dict[str, Tok]
    state: str
    def __init__(self, states: dict[str, list[Tok]], first: str) -> None: ...
    def lex(self, text: str) -> Iterator[tuple[str, str]]: ...

class JsLexer(Lexer):
    both_before: list[Tok]
    both_after: list[Tok]
    states: dict[str, list[Tok]]
    def __init__(self) -> None: ...

def prepare_js_for_gettext(js: str) -> str: ...
