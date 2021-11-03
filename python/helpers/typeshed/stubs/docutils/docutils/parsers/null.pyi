from typing import ClassVar, Tuple

from docutils import parsers

class Parser(parsers.Parser):
    config_section_dependencies: ClassVar[Tuple[str, ...]]
