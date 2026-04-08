from typing import Any

from django import template
from django.template.base import Parser, Token
from django.template.context import Context
from typing_extensions import override

register: Any

class AdminLogNode(template.Node):
    limit: str
    user: str
    varname: str
    def __init__(self, limit: str, varname: str, user: str | None) -> None: ...
    @override
    def render(self, context: Context) -> str: ...

def get_admin_log(parser: Parser, token: Token) -> AdminLogNode: ...
