from _typeshed import Incomplete
from typing import Generic, TypeVar, overload

_T = TypeVar("_T", str, bytes)

class Formatter(Generic[_T]):
    name: Incomplete
    aliases: Incomplete
    filenames: Incomplete
    unicodeoutput: bool
    style: Incomplete
    full: Incomplete
    title: Incomplete
    encoding: Incomplete
    options: Incomplete
    @overload
    def __init__(self: Formatter[str], *, encoding: None = None, outencoding: None = None, **options) -> None: ...
    @overload
    def __init__(self: Formatter[bytes], *, encoding: str, outencoding: None = None, **options) -> None: ...
    @overload
    def __init__(self: Formatter[bytes], *, encoding: None = None, outencoding: str, **options) -> None: ...
    def get_style_defs(self, arg: str = ""): ...
    def format(self, tokensource, outfile): ...
