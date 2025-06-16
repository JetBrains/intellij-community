from pathlib import Path
from re import Match, Pattern
from typing import ClassVar, Final

from docutils.parsers.rst import Directive
from docutils.parsers.rst.states import SpecializedBody

__docformat__: Final = "reStructuredText"

class Include(Directive):
    standard_include_path: Path

class Raw(Directive): ...
class Replace(Directive): ...

class Unicode(Directive):
    comment_pattern: Pattern[str]

class Class(Directive): ...

class Role(Directive):
    argument_pattern: Pattern[str]

class DefaultRole(Directive): ...
class Title(Directive): ...

class MetaBody(SpecializedBody):
    def field_marker(  # type: ignore[override]
        self, match: Match[str], context: list[str], next_state: str | None
    ) -> tuple[list[str], str | None, list[str]]: ...
    def parsemeta(self, match: Match[str]): ...

class Meta(Directive):
    SMkwargs: ClassVar[dict[str, tuple[MetaBody]]]

class Date(Directive): ...
class TestDirective(Directive): ...
