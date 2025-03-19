from _typeshed import Incomplete

class Formatter:
    pattern: Incomplete
    def __init__(self, pattern) -> None: ...
    def format(self, obj): ...
    def __call__(self, x): ...

class DecimalFormatter(Formatter):
    calcPlaces: Incomplete
    places: Incomplete
    dot: Incomplete
    comma: Incomplete
    prefix: Incomplete
    suffix: Incomplete
    def __init__(
        self,
        places: int = 2,
        decimalSep: str = ".",
        thousandSep: Incomplete | None = None,
        prefix: Incomplete | None = None,
        suffix: Incomplete | None = None,
    ) -> None: ...
    def format(self, num): ...
