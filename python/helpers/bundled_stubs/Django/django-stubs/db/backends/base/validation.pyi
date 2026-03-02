from typing import Any

from django.core.checks.messages import CheckMessage
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.fields import Field

class BaseDatabaseValidation:
    connection: BaseDatabaseWrapper
    def __init__(self, connection: BaseDatabaseWrapper) -> None: ...
    def check(self, **kwargs: Any) -> list[CheckMessage]: ...
    def check_field(self, field: Field, **kwargs: Any) -> list[CheckMessage]: ...
