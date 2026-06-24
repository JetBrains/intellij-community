from typing import Any, Optional, Union

from .aliases import AliasChoices, AliasPath


def Field(
    default: Any = ...,
    *,
    alias: Optional[str] = None,
    validation_alias: str | AliasPath | AliasChoices | None = None,
    serialization_alias: Optional[str] = None,
    title: Optional[str] = None,
    description: Optional[str] = None,
    frozen: bool | None = ...,
    **extra: Any
) -> Any:
    ...