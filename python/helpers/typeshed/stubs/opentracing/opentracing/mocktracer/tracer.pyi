from typing import Any

from ..scope_manager import ScopeManager
from ..span import Span
from ..tracer import Reference, Tracer
from .context import SpanContext
from .propagator import Propagator
from .span import MockSpan

class MockTracer(Tracer):
    def __init__(self, scope_manager: ScopeManager | None = ...) -> None: ...
    @property
    def active_span(self) -> MockSpan | None: ...
    def register_propagator(self, format: str, propagator: Propagator) -> None: ...
    def finished_spans(self) -> list[MockSpan]: ...
    def reset(self) -> None: ...
    def start_span(  # type: ignore[override]
        self,
        operation_name: str | None = ...,
        child_of: Span | SpanContext | None = ...,
        references: list[Reference] | None = ...,
        tags: dict[Any, Any] | None = ...,
        start_time: float | None = ...,
        ignore_active_span: bool = ...,
    ) -> MockSpan: ...
    def extract(self, format: str, carrier: dict[Any, Any]) -> SpanContext: ...
