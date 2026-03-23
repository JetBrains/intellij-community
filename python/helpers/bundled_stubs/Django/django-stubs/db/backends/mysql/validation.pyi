from typing import Any

from django.core.checks.messages import CheckMessage
from django.db.backends.base.validation import BaseDatabaseValidation
from django.db.backends.mysql.base import DatabaseWrapper
from django.db.models.fields import Field
from typing_extensions import override

class DatabaseValidation(BaseDatabaseValidation):
    connection: DatabaseWrapper
    @override
    def check(self, **kwargs: Any) -> list[CheckMessage]: ...
    def check_field_type(self, field: Field, field_type: str) -> list[CheckMessage]: ...
