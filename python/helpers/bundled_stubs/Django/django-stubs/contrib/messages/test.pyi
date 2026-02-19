from typing import Any

from django.contrib.messages.storage.base import BaseStorage
from django.http.response import HttpResponse

class MessagesTestMixin:
    def assertMessages(
        self, response: HttpResponse, expected_messages: list[Any] | BaseStorage, *, ordered: bool = True
    ) -> None: ...
