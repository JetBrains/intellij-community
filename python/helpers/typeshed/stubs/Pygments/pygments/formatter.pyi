from typing import Any

class Formatter:
    name: Any
    aliases: Any
    filenames: Any
    unicodeoutput: bool
    style: Any
    full: Any
    title: Any
    encoding: Any
    options: Any
    def __init__(self, **options) -> None: ...
    def get_style_defs(self, arg: str = ...): ...
    def format(self, tokensource, outfile): ...
