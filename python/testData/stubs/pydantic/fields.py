from typing import Any, Optional


def Field(
    default: Any = ...,
    *,
    alias: Optional[str] = None,
    validation_alias: Optional[str] = None,
    serialization_alias: Optional[str] = None,
    title: Optional[str] = None,
    description: Optional[str] = None,
    **extra: Any
) -> Any:
    ...