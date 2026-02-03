from logging import Logger
from typing import Any

from django.contrib.sessions.backends.db import SessionStore as DBStore

KEY_PREFIX: str

logger: Logger

class SessionStore(DBStore):
    cache_key_prefix: Any
    def __init__(self, session_key: str | None = None) -> None: ...
    @property
    def cache_key(self) -> str: ...
    async def acache_key(self) -> str: ...
