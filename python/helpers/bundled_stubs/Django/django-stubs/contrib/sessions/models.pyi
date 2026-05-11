from django.contrib.sessions.base_session import AbstractBaseSession, BaseSessionManager
from typing_extensions import TypeVar

_T = TypeVar("_T", bound=Session)

class SessionManager(BaseSessionManager[_T]): ...
class Session(AbstractBaseSession): ...
