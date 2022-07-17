from datetime import timedelta
from logging import Logger
from typing import Any, Callable, Iterable

_App = Any  # flask is not part of typeshed

LOG: Logger

class CORS:
    def __init__(
        self,
        app: Any | None = ...,
        *,
        resources: dict[str, dict[str, Any]] | list[str] | str | None = ...,
        origins: str | list[str] | None = ...,
        methods: str | list[str] | None = ...,
        expose_headers: str | list[str] | None = ...,
        allow_headers: str | list[str] | None = ...,
        supports_credentials: bool | None = ...,
        max_age: timedelta | int | str | None = ...,
        send_wildcard: bool | None = ...,
        vary_header: bool | None = ...,
        **kwargs: Any,
    ) -> None: ...
    def init_app(
        self,
        app: _App,
        *,
        resources: dict[str, dict[str, Any]] | list[str] | str = ...,
        origins: str | list[str] = ...,
        methods: str | list[str] = ...,
        expose_headers: str | list[str] = ...,
        allow_headers: str | list[str] = ...,
        supports_credentials: bool = ...,
        max_age: timedelta | int | str | None = ...,
        send_wildcard: bool = ...,
        vary_header: bool = ...,
        **kwargs: Any,
    ) -> None: ...

def make_after_request_function(resources: Iterable[tuple[str, dict[str, Any]]]) -> Callable[..., Any]: ...
