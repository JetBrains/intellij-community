from typing_extensions import Final

FORMULAE: Final[frozenset[str]]

def validate(formula: str) -> None: ...
