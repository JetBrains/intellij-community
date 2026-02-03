from typing import Any

from django.template import Node
from django.template.base import FilterExpression, NodeList, Parser, Token

register: Any

class CacheNode(Node):
    nodelist: NodeList
    expire_time_var: FilterExpression
    fragment_name: str
    vary_on: list[FilterExpression]
    cache_name: FilterExpression | None
    def __init__(
        self,
        nodelist: NodeList,
        expire_time_var: FilterExpression,
        fragment_name: str,
        vary_on: list[FilterExpression],
        cache_name: FilterExpression | None,
    ) -> None: ...

def do_cache(parser: Parser, token: Token) -> CacheNode: ...
