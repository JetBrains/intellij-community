from typing import Any, ClassVar

from django.db.models import DateTimeField, Func, UUIDField

class RandomUUID(Func):
    output_field: ClassVar[UUIDField[Any, Any]]

class TransactionNow(Func):
    output_field: ClassVar[DateTimeField[Any, Any]]
