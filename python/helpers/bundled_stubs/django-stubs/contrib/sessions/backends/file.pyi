from django.contrib.sessions.backends.base import SessionBase

class SessionStore(SessionBase):
    storage_path: str
    file_prefix: str
    def __init__(self, session_key: str | None = ...) -> None: ...
    def clean(self) -> None: ...
