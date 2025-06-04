from collections.abc import Sequence
from types import TracebackType
from typing import Any

from django.core.mail.message import EmailMessage
from typing_extensions import Self

class BaseEmailBackend:
    fail_silently: bool
    def __init__(self, fail_silently: bool = False, **kwargs: Any) -> None: ...
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
