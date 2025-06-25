from datetime import date

TIME_STRINGS: dict[str, str]
TIME_STRINGS_KEYS: list[str]
TIME_CHUNKS: list[int]
MONTHS_DAYS: tuple[int, ...]

def timesince(
    d: date,
    now: date | None = None,
    reversed: bool = False,
    time_strings: dict[str, str] | None = None,
    depth: int = 2,
) -> str: ...
def timeuntil(d: date, now: date | None = None, time_strings: dict[str, str] | None = None, depth: int = 2) -> str: ...
