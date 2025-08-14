import collections
from typing import Any

from django.template.base import FilterExpression, NodeList, Origin, Parser, Token
from django.template.context import Context
from django.utils.safestring import SafeString

from .base import Node, Template

register: Any
BLOCK_CONTEXT_KEY: str

class BlockContext:
    blocks: collections.defaultdict[str, list[BlockNode]]
    def __init__(self) -> None: ...
    def add_blocks(self, blocks: dict[str, BlockNode]) -> None: ...
    def pop(self, name: str) -> BlockNode: ...
    def push(self, name: str, block: BlockNode) -> None: ...
    def get_block(self, name: str) -> BlockNode: ...

class BlockNode(Node):
    context: Context
    name: str
    nodelist: NodeList
    origin: Origin
    parent: Node | None
    token: Token
    def __init__(self, name: str, nodelist: NodeList, parent: Node | None = None) -> None: ...
    def render(self, context: Context) -> SafeString: ...
    def super(self) -> SafeString: ...

class ExtendsNode(Node):
    origin: Origin
    token: Token
    must_be_first: bool
    context_key: str
    nodelist: NodeList
    parent_name: FilterExpression | Node
    template_dirs: list[Any] | None
    blocks: dict[str, BlockNode]
    def __init__(
        self, nodelist: NodeList, parent_name: FilterExpression | Node, template_dirs: list[Any] | None = None
    ) -> None: ...
    def find_template(self, template_name: str, context: Context) -> Template: ...
    def get_parent(self, context: Context) -> Template: ...
    def render(self, context: Context) -> Any: ...

class IncludeNode(Node):
    origin: Origin
    token: Token
    context_key: str
    template: FilterExpression
    extra_context: dict[str, FilterExpression]
    isolated_context: bool
    def __init__(
        self,
        template: FilterExpression,
        *args: Any,
        extra_context: Any | None = None,
        isolated_context: bool = False,
        **kwargs: Any,
    ) -> None: ...
    def render(self, context: Context) -> SafeString: ...

def do_block(parser: Parser, token: Token) -> BlockNode: ...
def construct_relative_path(current_template_name: str | None, relative_name: str) -> str: ...
def do_extends(parser: Parser, token: Token) -> ExtendsNode: ...
def do_include(parser: Parser, token: Token) -> IncludeNode: ...
