from collections import defaultdict
from typing import Any, Generic, Literal, TypeVar

_T = TypeVar("_T")

class Indirect(Generic[_T]):
    value: _T
    def __init__(self, initial: _T, /) -> None: ...

postprocessors: Indirect[dict[str, Any]]
extractors: Indirect[dict[str, Any]]
all_plugins_loaded: Indirect[bool]
plugin_specs: Indirect[dict[str, Any]]
plugin_dirs: Indirect[list[str]]
plugin_ies: Indirect[dict[str, Any]]
plugin_pps: Indirect[dict[str, Any]]
plugin_ies_overrides: Indirect[defaultdict[str, Any]]
IN_CLI: Indirect[bool]
LAZY_EXTRACTORS: Indirect[bool | None]
WINDOWS_VT_MODE: Indirect[Literal[False] | None]  # Code takes into account that only False here
