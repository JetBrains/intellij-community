from collections.abc import Iterable
from typing import Any, ClassVar, Literal, overload
from uuid import UUID

from django.db import models
from django.db.models.base import Model
from typing_extensions import deprecated

ADDITION: int
CHANGE: int
DELETION: int
ACTION_FLAG_CHOICES: Any

class LogEntryManager(models.Manager[LogEntry]):
    @deprecated("log_action() is deprecated and will be removed in Django 6.0. Use log_actions() instead.")
    def log_action(
        self,
        user_id: int | str | UUID,
        content_type_id: int,
        object_id: int | str | UUID,
        object_repr: str,
        action_flag: int,
        change_message: Any = ...,
    ) -> LogEntry: ...
    @overload
    def log_actions(
        self,
        user_id: int | str | UUID,
        queryset: Iterable[Model],
        action_flag: int,
        change_message: str | list[Any] = "",
        *,
        single_object: Literal[True] = ...,
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
    action_time: models.DateTimeField
    user: models.ForeignKey
    content_type: models.ForeignKey
    object_id: models.TextField
    object_repr: models.CharField
    action_flag: models.PositiveSmallIntegerField
    change_message: models.TextField
    objects: ClassVar[LogEntryManager]
    def is_addition(self) -> bool: ...
    def is_change(self) -> bool: ...
    def is_deletion(self) -> bool: ...
    def get_change_message(self) -> str: ...
    def get_edited_object(self) -> Model: ...
    def get_admin_url(self) -> str | None: ...
