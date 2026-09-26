from collections.abc import Iterable
from decimal import Decimal

def format(
    number: Decimal | float | str,
    decimal_sep: str,
    decimal_pos: int | None = None,
    grouping: int | Iterable[int] = 0,
    thousand_sep: str = "",
    force_grouping: bool = False,
    use_l10n: bool | None = None,
) -> str: ...
