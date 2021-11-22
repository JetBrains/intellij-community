from typing import Any

from docutils.parsers.rst import Directive

MODULEDOC: str
LEXERDOC: str
FMTERDOC: str
FILTERDOC: str

class PygmentsDoc(Directive):
    has_content: bool
    required_arguments: int
    optional_arguments: int
    final_argument_whitespace: bool
    option_spec: Any
    filenames: Any
    def run(self): ...
    def document_lexers(self): ...
    def document_formatters(self): ...
    def document_filters(self): ...

def setup(app) -> None: ...
