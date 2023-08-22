from typing import Any

LC_CTYPE: Any
PLURALS: Any
DEFAULT_PLURAL: Any

class _PluralTuple(tuple[int, str]):
    @property
    def num_plurals(self) -> int: ...
    @property
    def plural_expr(self) -> str: ...
    @property
    def plural_forms(self) -> str: ...

def get_plural(locale=...): ...
