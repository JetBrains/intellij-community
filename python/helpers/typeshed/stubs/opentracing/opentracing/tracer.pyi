from typing import Any, NamedTuple

from .scope import Scope
from .scope_manager import ScopeManager
from .span import Span, SpanContext

class Tracer:
    def __init__(self, scope_manager: ScopeManager | None = ...) -> None: ...
    @property
    def scope_manager(self) -> ScopeManager: ...
    @property
    def active_span(self) -> Span | None: ...
    def start_active_span(
        self,
        operation_name: str,
        child_of: Span | SpanContext | None = ...,
        references: list[Reference] | None = ...,
        tags: dict[Any, Any] | None = ...,
        start_time: float | None = ...,
        ignore_active_span: bool = ...,
        finish_on_close: bool = ...,
    ) -> Scope: ...
    def start_span(
        self,
        operation_name: str | None = ...,
        child_of: Span | SpanContext | None = ...,
        references: list[Reference] | None = ...,
        tags: dict[Any, Any] | None = ...,
        start_time: float | None = ...,
        ignore_active_span: bool = ...,
    ) -> Span: ...
    def inject(self, span_context: SpanContext, format: str, carrier: dict[Any, Any]) -> None: ...
    def extract(self, format: str, carrier: dict[Any, Any]) -> SpanContext: ...

class ReferenceType:
    CHILD_OF: str
    FOLLOWS_FROM: str

class Reference(NamedTuple):
    type: str
    referenced_context: SpanContext | None

def child_of(referenced_context: SpanContext | None = ...) -> Reference: ...
def follows_from(referenced_context: SpanContext | None = ...) -> Reference: ...
def start_child_span(
    parent_span: Span, operation_name: str, tags: dict[Any, Any] | None = ..., start_time: float | None = ...
) -> Span: ...
