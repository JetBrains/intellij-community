from collections.abc import Sequence
from types import TracebackType
from typing import Any, Self

from django.core.mail.message import EmailMessage

class BaseEmailBackend:
    fail_silently: bool
    def __init__(
        self,
        fail_silently: bool | object = ...,
        *,
        alias: str | None = None,
        _ignore_unknown_kwargs: set[str] | None = None,
        **kwargs: Any,
    ) -> None: ...
    def open(self) -> bool | None: ...
    def close(self) -> None: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        exc_tb: TracebackType | None,
    ) -> None: ...
    def send_messages(self, email_messages: Sequence[EmailMessage]) -> int: ...
