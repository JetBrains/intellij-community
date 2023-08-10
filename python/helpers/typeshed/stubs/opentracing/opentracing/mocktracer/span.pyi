from _typeshed import Self
from typing import Any

from ..span import Span
from ..tracer import Tracer
from .context import SpanContext
from .tracer import MockTracer

class MockSpan(Span):
    operation_name: str | None
    start_time: Any
    parent_id: int | None
    tags: dict[str, Any]
    finish_time: float
    finished: bool
    logs: list[LogData]
    def __init__(
        self,
        tracer: Tracer,
        operation_name: str | None = ...,
        context: SpanContext | None = ...,
        parent_id: int | None = ...,
        tags: dict[str, Any] | None = ...,
        start_time: float | None = ...,
    ) -> None: ...
    @property
    def tracer(self) -> MockTracer: ...
    @property
    def context(self) -> SpanContext: ...
    def set_operation_name(self: Self, operation_name: str) -> Self: ...
    def set_tag(self: Self, key: str, value: str | bool | float) -> Self: ...
    def log_kv(self: Self, key_values: dict[str, Any], timestamp: float | None = ...) -> Self: ...
    def set_baggage_item(self: Self, key: str, value: str) -> Self: ...

class LogData:
    key_values: dict[str, Any]
    timestamp: float | None
    def __init__(self, key_values: dict[str, Any], timestamp: float | None = ...) -> None: ...
