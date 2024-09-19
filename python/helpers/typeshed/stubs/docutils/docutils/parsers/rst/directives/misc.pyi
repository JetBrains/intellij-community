from _typeshed import Incomplete
from pathlib import Path
from re import Pattern
from typing import ClassVar

from docutils.parsers.rst import Directive
from docutils.parsers.rst.states import SpecializedBody

__docformat__: str

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

# SpecializedBody has not yet been stubbed
class MetaBody(SpecializedBody):  # pyright: ignore[reportUntypedBaseClass]
    def __getattr__(self, name: str) -> Incomplete: ...

class Meta(Directive):
    SMkwargs: ClassVar[dict[str, tuple[MetaBody]]]

class Date(Directive): ...
class TestDirective(Directive): ...
