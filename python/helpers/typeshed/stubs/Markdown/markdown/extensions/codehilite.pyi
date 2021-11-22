from typing import Any

from markdown.extensions import Extension
from markdown.treeprocessors import Treeprocessor

pygments: bool

def parse_hl_lines(expr): ...

class CodeHilite:
    src: Any
    lang: Any
    linenums: Any
    guess_lang: Any
    css_class: Any
    style: Any
    noclasses: Any
    tab_length: Any
    hl_lines: Any
    use_pygments: Any
    options: dict[str, Any]
    def __init__(
        self,
        src: Any | None = ...,
        *,
        linenums: Any | None = ...,
        guess_lang: bool = ...,
        css_class: str = ...,
        lang: Any | None = ...,
        style: str = ...,
        noclasses: bool = ...,
        tab_length: int = ...,
        hl_lines: Any | None = ...,
        use_pygments: bool = ...,
        **options: Any,
    ) -> None: ...
    def hilite(self): ...

class HiliteTreeprocessor(Treeprocessor):
    def code_unescape(self, text): ...

class CodeHiliteExtension(Extension):
    def __init__(self, **kwargs) -> None: ...

def makeExtension(**kwargs): ...
