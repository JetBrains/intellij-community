from typing import TypeVar

from django.contrib.sessions.base_session import AbstractBaseSession, BaseSessionManager

_T = TypeVar("_T", bound=Session)

class SessionManager(BaseSessionManager[_T]): ...
class Session(AbstractBaseSession): ...
