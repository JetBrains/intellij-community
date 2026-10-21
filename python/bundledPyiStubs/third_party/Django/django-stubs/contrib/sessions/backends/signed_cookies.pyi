from django.contrib.sessions.backends.base import SessionBase
from typing_extensions import override

class SessionStore(SessionBase):
    @override
    def exists(self, session_key: str | None = None) -> bool: ...
    @override
    async def aexists(self, session_key: str | None = None) -> bool: ...
