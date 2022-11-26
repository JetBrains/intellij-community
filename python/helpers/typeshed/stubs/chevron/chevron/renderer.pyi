from _typeshed import StrPath, SupportsRead
from collections.abc import MutableSequence, Sequence
from typing import Any

g_token_cache: dict[str, list[tuple[str, str]]]  # undocumented

def render(
    template: SupportsRead[str] | str | Sequence[tuple[str, str]] = ...,
    data: dict[str, Any] = ...,
    partials_path: StrPath | None = ...,
    partials_ext: str = ...,
    partials_dict: dict[str, str] = ...,
    padding: str = ...,
    def_ldel: str | None = ...,
    def_rdel: str | None = ...,
    scopes: MutableSequence[int] | None = ...,
    warn: bool = ...,
) -> str: ...
