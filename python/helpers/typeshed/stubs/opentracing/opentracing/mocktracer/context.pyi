from _typeshed import Self

import opentracing

class SpanContext(opentracing.SpanContext):
    trace_id: int | None
    span_id: int | None
    def __init__(self, trace_id: int | None = ..., span_id: int | None = ..., baggage: dict[str, str] | None = ...) -> None: ...
    @property
    def baggage(self) -> dict[str, str]: ...
    def with_baggage_item(self: Self, key: str, value: str) -> Self: ...
