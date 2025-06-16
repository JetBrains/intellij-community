import arrow

class DateMathException(Exception): ...

def parse(
    expression: str, now: arrow.Arrow | None = None, tz: str = "UTC", type: str | None = None, roundDown: bool = True
) -> arrow.Arrow: ...
def __getattr__(name: str): ...  # incomplete module
