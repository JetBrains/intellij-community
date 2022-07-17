import sys
from typing import Callable
from xml.etree.ElementTree import Element

XINCLUDE: str
XINCLUDE_INCLUDE: str
XINCLUDE_FALLBACK: str

if sys.version_info >= (3, 9):
    DEFAULT_MAX_INCLUSION_DEPTH: int

class FatalIncludeError(SyntaxError): ...

def default_loader(href: str | bytes | int, parse: str, encoding: str | None = ...) -> str | Element: ...

# TODO: loader is of type default_loader ie it takes a callable that has the
# same signature as default_loader. But default_loader has a keyword argument
# Which can't be represented using Callable...
if sys.version_info >= (3, 9):
    def include(
        elem: Element, loader: Callable[..., str | Element] | None = ..., base_url: str | None = ..., max_depth: int | None = ...
    ) -> None: ...

    class LimitedRecursiveIncludeError(FatalIncludeError): ...

else:
    def include(elem: Element, loader: Callable[..., str | Element] | None = ...) -> None: ...
