from collections.abc import Mapping, Sequence
from typing import Any

from django.http.request import HttpRequest
from django.template.exceptions import TemplateDoesNotExist as TemplateDoesNotExist
from django.utils.safestring import SafeString

from .backends.base import _EngineTemplate

def get_template(template_name: str, using: str | None = ...) -> _EngineTemplate: ...
def select_template(template_name_list: Sequence[str] | str, using: str | None = ...) -> Any: ...
def render_to_string(
    template_name: Sequence[str] | str,
    context: Mapping[str, Any] | None = ...,
    request: HttpRequest | None = ...,
    using: str | None = ...,
) -> SafeString: ...
