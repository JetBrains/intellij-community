import datetime as dt
from collections.abc import Iterable
from typing import Any, ClassVar, Literal, overload
from uuid import UUID

from django.contrib.auth.models import _User
from django.contrib.contenttypes.models import ContentType
from django.db import models
from django.db.models.base import Model
from django.db.models.expressions import Combinable

ADDITION: int
CHANGE: int
DELETION: int
ACTION_FLAG_CHOICES: Any

class LogEntryManager(models.Manager[LogEntry]):
    @overload
    def log_actions(
        self,
        user_id: int | str | UUID,
        queryset: Iterable[Model],
        action_flag: int,
        change_message: str | list[Any] = "",
        *,
        single_object: Literal[True],
    ) -> LogEntry: ...
    @overload
    def log_actions(
        self,
        user_id: int | str | UUID,
        queryset: Iterable[Model],
        action_flag: int,
        change_message: str | list[Any] = "",
        *,
        single_object: Literal[False] = ...,
    ) -> list[LogEntry]: ...

class LogEntry(models.Model):
    action_time: models.DateTimeField[str | dt.datetime | dt.date | Combinable, dt.datetime]
    user: models.ForeignKey[_User | Combinable, _User]
    content_type: models.ForeignKey[ContentType | Combinable | None, ContentType | None]
    object_id: models.TextField[str | Combinable | None, str | None]
    object_repr: models.CharField[str | int | Combinable, str]
    action_flag: models.PositiveSmallIntegerField[float | int | str | Combinable, int]
    change_message: models.TextField[str | Combinable, str]
    objects: ClassVar[LogEntryManager]
    def is_addition(self) -> bool: ...
    def is_change(self) -> bool: ...
    def is_deletion(self) -> bool: ...
    def get_change_message(self) -> str: ...
    def get_edited_object(self) -> Model: ...
    def get_admin_url(self) -> str | None: ...
