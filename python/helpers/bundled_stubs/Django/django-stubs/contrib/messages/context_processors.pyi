from typing import Any

from django.contrib.messages.storage.base import BaseStorage
from django.http.request import HttpRequest

def messages(request: HttpRequest) -> dict[str, dict[str, int] | list[Any] | BaseStorage]: ...
