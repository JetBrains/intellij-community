from typing import Any

from django.contrib.sessions.backends.db import SessionStore as DBStore

KEY_PREFIX: str

class SessionStore(DBStore):
    cache_key_prefix: Any
    def __init__(self, session_key: str | None = ...) -> None: ...
    @property
    def cache_key(self) -> str: ...
