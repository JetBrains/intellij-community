from typing import ClassVar

from docutils import parsers

class Parser(parsers.Parser):
    supported: ClassVar[tuple[str, ...]]
    config_section_dependencies: ClassVar[tuple[str, ...]]
