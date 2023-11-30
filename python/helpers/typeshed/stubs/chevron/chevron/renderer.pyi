from _typeshed import StrPath, SupportsRead
from collections.abc import MutableSequence, Sequence
from typing import Any
from typing_extensions import Literal

g_token_cache: dict[str, list[tuple[str, str]]]  # undocumented
python3: Literal[True]

def unicode(x: str, y: str) -> str: ...
def render(
    template: SupportsRead[str] | str | Sequence[tuple[str, str]] = "",
    data: dict[str, Any] = {},
    partials_path: StrPath | None = ".",
    partials_ext: str = "mustache",
    partials_dict: dict[str, str] = {},
    padding: str = "",
    def_ldel: str | None = "{{",
    def_rdel: str | None = "}}",
    scopes: MutableSequence[int] | None = None,
    warn: bool = False,
) -> str: ...
