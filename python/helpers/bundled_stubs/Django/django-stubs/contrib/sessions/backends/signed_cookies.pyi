from django.contrib.sessions.backends.base import SessionBase

class SessionStore(SessionBase):
    async def aexists(self, session_key: str | None = None) -> bool: ...
