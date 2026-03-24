from collections.abc import Callable, Collection, Mapping
from typing import Any, TypeVar

_F = TypeVar("_F", bound=Callable[..., Any])

def csp_override(config: Mapping[str, Collection[str] | str]) -> Callable[[_F], _F]: ...
def csp_report_only_override(config: Mapping[str, Collection[str] | str]) -> Callable[[_F], _F]: ...
