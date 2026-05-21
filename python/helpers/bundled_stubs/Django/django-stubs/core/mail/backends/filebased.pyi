from typing import Any

from django.core.mail.backends.console import EmailBackend as ConsoleEmailBackend

class EmailBackend(ConsoleEmailBackend):
    file_path: str
    def __init__(self, *args: Any, file_path: str | None = None, **kwargs: Any) -> None: ...
