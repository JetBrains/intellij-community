from collections.abc import Iterable
from decimal import Decimal

def format(
    number: Decimal | float | str,
    decimal_sep: str,
    decimal_pos: int | None = ...,
    grouping: int | Iterable[int] = ...,
    thousand_sep: str = ...,
    force_grouping: bool = ...,
    use_l10n: bool | None = ...,
) -> str: ...
