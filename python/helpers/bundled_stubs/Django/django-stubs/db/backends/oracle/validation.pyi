from typing import Any

from django.db.backends.base.validation import BaseDatabaseValidation
from django.db.backends.oracle.base import DatabaseWrapper

class DatabaseValidation(BaseDatabaseValidation):
    connection: DatabaseWrapper
    def check_field_type(self, field: Any, field_type: Any) -> Any: ...
