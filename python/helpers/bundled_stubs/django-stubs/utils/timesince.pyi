from datetime import date

TIME_STRINGS: dict[str, str]
TIME_STRINGS_KEYS: list[str]
TIME_CHUNKS: list[int]
MONTHS_DAYS: tuple[int, ...]

def timesince(
    d: date,
    now: date | None = ...,
    reversed: bool = ...,
    time_strings: dict[str, str] | None = ...,
    depth: int = ...,
) -> str: ...
def timeuntil(d: date, now: date | None = ..., time_strings: dict[str, str] | None = ..., depth: int = ...) -> str: ...
