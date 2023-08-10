import collections.abc
from _typeshed import Self
from collections.abc import Callable, Mapping
from re import Pattern
from typing import Any, Union
from typing_extensions import TypeAlias

from parsimonious.exceptions import ParseError
from parsimonious.grammar import Grammar
from parsimonious.nodes import Node
from parsimonious.utils import StrAndRepr

_CALLABLE_RETURN_TYPE: TypeAlias = Union[int, tuple[int, list[Node]], Node, None]
_CALLABLE_TYPE: TypeAlias = (
    Callable[[str, int], _CALLABLE_RETURN_TYPE]
    | Callable[[str, int, Mapping[tuple[int, int], Node], ParseError, Grammar], _CALLABLE_RETURN_TYPE]
)

def is_callable(value: object) -> bool: ...
def expression(callable: _CALLABLE_TYPE, rule_name: str, grammar: Grammar) -> Expression: ...

IN_PROGRESS: object

class Expression(StrAndRepr):
    name: str
    identity_tuple: tuple[str]
    def __init__(self, name: str = ...) -> None: ...
    def resolve_refs(self: Self, rule_map: Mapping[str, Expression]) -> Self: ...
    def parse(self, text: str, pos: int = ...) -> Node: ...
    def match(self, text: str, pos: int = ...) -> Node: ...
    def match_core(self, text: str, pos: int, cache: Mapping[tuple[int, int], Node], error: ParseError) -> Node: ...
    def as_rule(self) -> str: ...

class Literal(Expression):
    literal: str
    identity_tuple: tuple[str, str]  # type: ignore[assignment]
    def __init__(self, literal: str, name: str = ...) -> None: ...

class TokenMatcher(Literal): ...

class Regex(Expression):
    re: Pattern[str]
    identity_tuple: tuple[str, Pattern[str]]  # type: ignore[assignment]
    def __init__(
        self,
        pattern: str,
        name: str = ...,
        ignore_case: bool = ...,
        locale: bool = ...,
        multiline: bool = ...,
        dot_all: bool = ...,
        unicode: bool = ...,
        verbose: bool = ...,
        ascii: bool = ...,
    ) -> None: ...

class Compound(Expression):
    members: collections.abc.Sequence[Expression]
    def __init__(self, *members: Expression, **kwargs: Any) -> None: ...

class Sequence(Compound): ...
class OneOf(Compound): ...

class Lookahead(Compound):
    negativity: bool
    def __init__(self, member: Expression, *, negative: bool = ..., **kwargs: Any) -> None: ...

def Not(term: Expression) -> Lookahead: ...

class Quantifier(Compound):
    min: int
    max: float
    def __init__(self, member: Expression, *, min: int = ..., max: float = ..., name: str = ..., **kwargs: Any) -> None: ...

def ZeroOrMore(member: Expression, name: str = ...) -> Quantifier: ...
def OneOrMore(member: Expression, name: str = ..., min: int = ...) -> Quantifier: ...
def Optional(member: Expression, name: str = ...) -> Quantifier: ...
