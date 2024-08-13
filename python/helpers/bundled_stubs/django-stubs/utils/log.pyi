import logging.config
from collections.abc import Callable
from logging import Logger, LogRecord
from typing import Any

from django.core.management.color import Style
from django.http import HttpRequest, HttpResponse
from django.utils.functional import _StrOrPromise

request_logger: Logger
DEFAULT_LOGGING: Any

def configure_logging(logging_config: str, logging_settings: dict[str, Any]) -> None: ...

class AdminEmailHandler(logging.Handler):
    include_html: bool
    email_backend: str | None
    def __init__(
        self,
        include_html: bool = ...,
        email_backend: str | None = ...,
        reporter_class: str | None = ...,
    ) -> None: ...
    def send_mail(self, subject: _StrOrPromise, message: _StrOrPromise, *args: Any, **kwargs: Any) -> None: ...
    def connection(self) -> Any: ...
    def format_subject(self, subject: str) -> str: ...

class CallbackFilter(logging.Filter):
    callback: Callable[[str | LogRecord], bool]
    def __init__(self, callback: Callable[[str | LogRecord], bool]) -> None: ...
    def filter(self, record: str | LogRecord) -> bool: ...

class RequireDebugFalse(logging.Filter):
    def filter(self, record: str | LogRecord) -> bool: ...

class RequireDebugTrue(logging.Filter):
    def filter(self, record: str | LogRecord) -> bool: ...

class ServerFormatter(logging.Formatter):
    default_time_format: str
    style: Style
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def uses_server_time(self) -> bool: ...

def log_response(
    message: str,
    *args: Any,
    response: HttpResponse | None = ...,
    request: HttpRequest | None = ...,
    logger: Logger = ...,
    level: str | None = ...,
    exception: BaseException | None = ...,
) -> None: ...
