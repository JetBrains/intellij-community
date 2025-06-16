import re
from _typeshed import Incomplete
from collections.abc import Callable
from typing import Protocol, type_check_only
from typing_extensions import Never

from docutils import nodes

@type_check_only
class _RegexPatternSub(Protocol):
    # Matches the signature of the bound instance method `re.Pattern[str].sub` exactly
    def __call__(self, /, repl: str | Callable[[re.Match[str]], str], string: str, count: int = 0) -> str: ...

class Translator(nodes.NodeVisitor):
    def visit_admonition(self, node: nodes.admonition, name: str | None = None) -> None: ...
    def visit_comment(self, node: nodes.comment, sub: _RegexPatternSub = ...) -> Never: ...
    def __getattr__(self, name: str, /) -> Incomplete: ...

def __getattr__(name: str): ...  # incomplete module
