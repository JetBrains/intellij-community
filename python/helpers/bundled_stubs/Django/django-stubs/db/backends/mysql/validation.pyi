from typing import Any

from django.db.backends.base.validation import BaseDatabaseValidation
from django.db.backends.mysql.base import DatabaseWrapper

class DatabaseValidation(BaseDatabaseValidation):
    connection: DatabaseWrapper
    def check(self, **kwargs: Any) -> Any: ...
    def check_field_type(self, field: Any, field_type: Any) -> Any: ...
