from collections.abc import Callable
from typing import Any

from django.template.base import Parser, Token
from django.template.context import Context
from django.template.library import InclusionNode
from django.utils.safestring import SafeString

class InclusionAdminNode(InclusionNode):
    args: list[Any]
    func: Callable
    kwargs: dict[str, Any]
    takes_context: bool
    template_name: str
    def __init__(
        self, parser: Parser, token: Token, func: Callable, template_name: str, takes_context: bool = ...
    ) -> None: ...
    def render(self, context: Context) -> SafeString: ...
