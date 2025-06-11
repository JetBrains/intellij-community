from typing import Any

from django.contrib.sessions.backends.base import SessionBase

KEY_PREFIX: str

class SessionStore(SessionBase):
    cache_key_prefix: Any
    def __init__(self, session_key: str | None = None) -> None: ...
    @property
    def cache_key(self) -> str: ...
    async def acache_key(self) -> str: ...
