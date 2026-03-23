import json
from collections.abc import Callable, Sequence
from typing import Any

from django.contrib.messages.storage.base import BaseStorage
from typing_extensions import override

class MessageEncoder(json.JSONEncoder):
    allow_nan: bool
    check_circular: bool
    ensure_ascii: bool
    skipkeys: bool
    sort_keys: bool
    message_key: str
    @override
    def default(self, obj: Any) -> Any: ...

class MessageDecoder(json.JSONDecoder):
    def process_messages(self, obj: Any) -> Any: ...
    @override
    def decode(self, s: str, **kwargs: Any) -> Any: ...  # type: ignore[override]

class MessagePartSerializer:
    def dumps(self, obj: Any) -> Sequence[str]: ...

class MessagePartGatherSerializer:
    def dumps(self, obj: Any) -> bytes: ...

class MessageSerializer:
    def loads(self, data: bytes | bytearray) -> Any: ...

class CookieStorage(BaseStorage):
    cookie_name: str
    max_cookie_size: int
    not_finished: str
    not_finished_json: str
    key_salt: str

def bisect_keep_left(a: list[int], fn: Callable[[list[int]], bool]) -> int: ...
def bisect_keep_right(a: list[int], fn: Callable[[list[int]], bool]) -> int: ...
