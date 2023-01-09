from collections.abc import Callable
from datetime import timedelta
from logging import Logger
from typing import Any
from typing_extensions import ParamSpec

_P = ParamSpec("_P")

LOG: Logger

def cross_origin(
    *args: Any,
    origins: str | list[str] | None = ...,
    methods: str | list[str] | None = ...,
    expose_headers: str | list[str] | None = ...,
    allow_headers: str | list[str] | None = ...,
    supports_credentials: bool | None = ...,
    max_age: timedelta | int | str | None = ...,
    send_wildcard: bool | None = ...,
    vary_header: bool | None = ...,
    automatic_options: bool | None = ...,
) -> Callable[[Callable[_P, Any]], Callable[_P, Any]]: ...
