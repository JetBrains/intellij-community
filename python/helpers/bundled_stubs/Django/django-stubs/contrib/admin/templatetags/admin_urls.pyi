from typing import Any
from uuid import UUID

from django.db.models.options import Options
from django.template.context import RequestContext
from django.utils.safestring import SafeString

register: Any

def admin_urlname(value: Options, arg: SafeString) -> str: ...
def admin_urlquote(value: int | str | UUID) -> int | str | UUID: ...
def add_preserved_filters(
    context: dict[str, Options | str] | RequestContext,
    url: str,
    popup: bool = ...,
    to_field: str | None = ...,
) -> str: ...
