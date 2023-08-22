from typing import Any

class UnknownUnitError(ValueError):
    def __init__(self, unit, locale) -> None: ...

def get_unit_name(measurement_unit, length: str = ..., locale=...): ...
def format_unit(value, measurement_unit, length: str = ..., format: Any | None = ..., locale=...): ...
def format_compound_unit(
    numerator_value,
    numerator_unit: Any | None = ...,
    denominator_value: int = ...,
    denominator_unit: Any | None = ...,
    length: str = ...,
    format: Any | None = ...,
    locale=...,
): ...
