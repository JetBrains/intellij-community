from collections.abc import Sequence

from docutils.parsers.rst import Directive

class Contents(Directive):
    backlinks_values: Sequence[str]

class Sectnum(Directive): ...
class Header(Directive): ...
class Footer(Directive): ...
