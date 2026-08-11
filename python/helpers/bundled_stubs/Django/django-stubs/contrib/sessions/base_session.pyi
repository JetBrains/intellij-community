import datetime as dt
from typing import Any, ClassVar

from django.contrib.sessions.backends.base import SessionBase
from django.db import models
from django.db.models.expressions import Combinable
from typing_extensions import Self, TypeVar

_T = TypeVar("_T", bound=AbstractBaseSession)

class BaseSessionManager(models.Manager[_T]):
    def encode(self, session_dict: dict[str, Any]) -> str: ...
    def save(self, session_key: str, session_dict: dict[str, Any], expire_date: dt.datetime) -> _T: ...

class AbstractBaseSession(models.Model):
    session_key: models.CharField[str | int | Combinable, str]
    session_data: models.TextField[str | Combinable, str]
    expire_date: models.DateTimeField[str | dt.datetime | dt.date | Combinable, dt.datetime]
    objects: ClassVar[BaseSessionManager[Self]]

    class Meta:
        abstract: ClassVar[bool]
        verbose_name: ClassVar[str]
        verbose_name_plural: ClassVar[str]

    @classmethod
    def get_session_store_class(cls) -> type[SessionBase] | None: ...
    def get_decoded(self) -> dict[str, Any]: ...
