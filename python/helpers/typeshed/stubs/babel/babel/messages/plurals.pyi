from typing import Any

LC_CTYPE: Any
PLURALS: Any
DEFAULT_PLURAL: Any

class _PluralTuple(tuple[int, str]):
    num_plurals: Any
    plural_expr: Any
    plural_forms: Any

def get_plural(locale=...): ...
