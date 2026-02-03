from typing import NamedTuple
from dataclasses import KW_ONLY


class NoDataclass(NamedTuple):
    y: int
    _: KW_ONLY    # no effect
    <error descr="Fields with a default value must come after any fields without a default.">z</error>: int = 1
    bar2: int
