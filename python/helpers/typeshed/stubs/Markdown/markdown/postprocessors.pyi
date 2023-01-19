from re import Pattern
from typing import Any

from . import util

def build_postprocessors(md, **kwargs): ...

class Postprocessor(util.Processor):
    def run(self, text) -> Any: ...

class RawHtmlPostprocessor(Postprocessor):
    def isblocklevel(self, html): ...

class AndSubstitutePostprocessor(Postprocessor): ...

class UnescapePostprocessor(Postprocessor):  # deprecated
    RE: Pattern[str]
    def unescape(self, m): ...
