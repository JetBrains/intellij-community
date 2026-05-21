from django.core.checks.messages import CheckMessage
from django.db.backends.base.validation import BaseDatabaseValidation
from django.db.backends.oracle.base import DatabaseWrapper
from django.db.models.fields import Field

class DatabaseValidation(BaseDatabaseValidation):
    connection: DatabaseWrapper
    def check_field_type(self, field: Field, field_type: str) -> list[CheckMessage]: ...
