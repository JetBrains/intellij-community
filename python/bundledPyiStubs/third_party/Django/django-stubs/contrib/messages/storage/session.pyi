from collections.abc import Sequence
from typing import Any

from django.contrib.messages.storage.base import BaseStorage
from django.http.request import HttpRequest

class SessionStorage(BaseStorage):
    session_key: str
    def __init__(self, request: HttpRequest, *args: Any, **kwargs: Any) -> None: ...
    def serialize_messages(self, messages: Sequence[Any]) -> str: ...
    def deserialize_messages(self, data: list[Any] | str | None) -> list[Any] | None: ...
