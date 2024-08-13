from collections.abc import Callable
from typing import Any

from django.utils.safestring import SafeString

docutils_is_available: bool

def get_view_name(view_func: Callable) -> str: ...
def parse_docstring(docstring: str) -> tuple[str, str, dict[str, str]]: ...
def parse_rst(text: str, default_reference_context: Any, thing_being_parsed: Any | None = ...) -> SafeString: ...

ROLES: dict[str, str]

def create_reference_role(rolename: str, urlbase: str) -> None: ...
def default_reference_role(
    name: str,
    rawtext: str,
    text: str,
    lineno: Any,
    inliner: Any,
    options: Any | None = ...,
    content: Any | None = ...,
) -> tuple[list[Any], list[Any]]: ...

named_group_matcher: Any
unnamed_group_matcher: Any

def replace_named_groups(pattern: str) -> str: ...
def replace_unnamed_groups(pattern: str) -> str: ...
