from typing import Any

from .elements import conv as conv

class ConventionDict:
    const: Any
    table: Any
    convention: Any
    def __init__(self, const, table, convention) -> None: ...
    def __getitem__(self, key): ...
