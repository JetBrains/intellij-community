from typing import (
    ClassVar,
    Iterator,
    Literal,
    Self,
)

__all__: tuple[str, ...] = ("Interpolation", "Template")


class Interpolation:
    """
    Represents a single interpolation embedded in an f‑string / template string.
    """

    value: object
    """The evaluated result of the interpolation."""

    expression: str
    """The original expression text (unparsed)."""

    conversion: Literal["a", "r", "s"] | None
    """Optional conversion character; ``None`` if omitted."""

    format_spec: str
    """The trailing format‑spec (after ``':'``), empty string if none."""

    __match_args__: ClassVar[tuple[str, ...]] = (
        "value",
        "expression",
        "conversion",
        "format_spec",
    )

    def __new__(
            cls,
            value: object,
            expression: str,
            conversion: Literal["a", "r", "s"] | None = None,
            format_spec: str = "",
    ) -> Self: ...

    def __repr__(self) -> str: ...


class Template:
    """
    Immutable template string object returned by the parser.

    A *template* alternates raw string parts and :class:`Interpolation`
    objects, preserving exact source‑order.
    """

    strings: tuple[str, ...]
    """
    Tuple of plain string segments.  Always length ``len(interpolations) + 1``.
    """

    interpolations: tuple[Interpolation, ...]
    """Tuple of :class:`Interpolation` objects (may be empty)."""

    def __new__(cls, *parts: str | Interpolation) -> Self: ...

    """
    Build a Template from an arbitrary shuffle of ``str`` and
    :class:`Interpolation`.  The order is preserved exactly as given.
    """

    @property
    def values(self) -> tuple[object, ...]:
        """Shortcut: ``tuple(i.value for i in self.interpolations)`` ."""
        ...

    def __iter__(self) -> Iterator[str | Interpolation]: ...

    def __add__(self, other: str | "Template") -> Self: ...

    def __radd__(self, other: str) -> Self: ...

    def __repr__(self) -> str: ...
