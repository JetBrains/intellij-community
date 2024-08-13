from collections.abc import Callable, Collection, Iterable, Mapping, Sequence, Sized
from typing import Any, TypeVar, overload

from django.template.base import FilterExpression, Origin, Parser, Token
from django.template.context import Context
from django.utils.safestring import SafeString

from .base import Node, Template

class InvalidTemplateLibrary(Exception): ...

_C = TypeVar("_C", bound=Callable[..., Any])

class Library:
    filters: dict[str, Callable]
    tags: dict[str, Callable]
    def __init__(self) -> None: ...
    @overload
    def tag(self, name: _C) -> _C: ...
    @overload
    def tag(self, name: str, compile_function: _C) -> _C: ...
    @overload
    def tag(self, name: str | None = ..., compile_function: None = ...) -> Callable[[_C], _C]: ...
    def tag_function(self, func: _C) -> _C: ...
    @overload
    def filter(self, name: _C, filter_func: None = ..., **flags: Any) -> _C: ...
    @overload
    def filter(self, name: str | None, filter_func: _C, **flags: Any) -> _C: ...
    @overload
    def filter(self, name: str | None = ..., filter_func: None = ..., **flags: Any) -> Callable[[_C], _C]: ...
    @overload
    def simple_tag(self, func: _C) -> _C: ...
    @overload
    def simple_tag(self, takes_context: bool | None = ..., name: str | None = ...) -> Callable[[_C], _C]: ...
    def inclusion_tag(
        self,
        filename: Template | str,
        func: Callable | None = ...,
        takes_context: bool | None = ...,
        name: str | None = ...,
    ) -> Callable[[_C], _C]: ...

class TagHelperNode(Node):
    func: Any
    takes_context: Any
    args: Any
    kwargs: Any
    def __init__(
        self,
        func: Callable,
        takes_context: bool | None,
        args: list[FilterExpression],
        kwargs: dict[str, FilterExpression],
    ) -> None: ...
    def get_resolved_arguments(self, context: Context) -> tuple[list[int], dict[str, SafeString | int]]: ...

class SimpleNode(TagHelperNode):
    args: list[FilterExpression]
    func: Callable
    kwargs: dict[str, FilterExpression]
    origin: Origin
    takes_context: bool | None
    token: Token
    target_var: str | None
    def __init__(
        self,
        func: Callable,
        takes_context: bool | None,
        args: list[FilterExpression],
        kwargs: dict[str, FilterExpression],
        target_var: str | None,
    ) -> None: ...

class InclusionNode(TagHelperNode):
    args: list[FilterExpression]
    func: Callable
    kwargs: dict[str, FilterExpression]
    origin: Origin
    takes_context: bool | None
    token: Token
    filename: Template | str
    def __init__(
        self,
        func: Callable,
        takes_context: bool | None,
        args: list[FilterExpression],
        kwargs: dict[str, FilterExpression],
        filename: Template | str | None,
    ) -> None: ...

def parse_bits(
    parser: Parser,
    bits: Iterable[str],
    params: Sequence[str],
    varargs: str | None,
    varkw: str | None,
    defaults: Sized | None,
    kwonly: Collection[str],
    kwonly_defaults: Mapping[str, int] | None,
    takes_context: bool | None,
    name: str,
) -> tuple[list[FilterExpression], dict[str, FilterExpression]]: ...
def import_library(name: str) -> Library: ...
