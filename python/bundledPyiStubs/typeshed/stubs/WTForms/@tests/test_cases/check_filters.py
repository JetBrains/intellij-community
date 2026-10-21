from __future__ import annotations

from wtforms import Field, Form


class Filter1:
    def __call__(self, value: object) -> None: ...


class Filter2:
    def __call__(self, input: None) -> None: ...


def not_a_filter(a: object, b: object) -> None: ...


def also_not_a_filter() -> None: ...


# we should accept any mapping of sequences, we can't really validate
# the filter functions when it's this nested
form = Form()
form.process(extra_filters={"foo": (str.upper, str.strip, int), "bar": (Filter1(), Filter2())})
form.process(extra_filters={"foo": [str.upper, str.strip, int], "bar": [Filter1(), Filter2()]})

# regardless of how we pass the filters into Field it should work
field = Field(filters=(str.upper, str.lower, int))
Field(filters=(Filter1(), Filter2()))
Field(filters=[str.upper, str.lower, int])
Field(filters=[Filter1(), Filter2()])
field.process(None, extra_filters=(str.upper, str.lower, int))
field.process(None, extra_filters=(Filter1(), Filter2()))
field.process(None, extra_filters=[str.upper, str.lower, int])
field.process(None, extra_filters=[Filter1(), Filter2()])

# but if we pass in some callables with an incompatible param spec
# then we should get type errors
Field(filters=(str.upper, str.lower, int, not_a_filter))  # type: ignore
Field(filters=(Filter1(), Filter2(), also_not_a_filter))  # type: ignore
Field(filters=[str.upper, str.lower, int, also_not_a_filter])  # type: ignore
Field(filters=[Filter1(), Filter2(), not_a_filter])  # type: ignore
field.process(None, extra_filters=(str.upper, str.lower, int, not_a_filter))  # type: ignore
field.process(None, extra_filters=(Filter1(), Filter2(), also_not_a_filter))  # type: ignore
field.process(None, extra_filters=[str.upper, str.lower, int, also_not_a_filter])  # type: ignore
field.process(None, extra_filters=[Filter1(), Filter2(), not_a_filter])  # type: ignore
