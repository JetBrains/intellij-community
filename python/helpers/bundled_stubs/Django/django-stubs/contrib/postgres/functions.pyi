from typing import ClassVar

from django.db.models import DateTimeField, Func, UUIDField

class RandomUUID(Func):
    output_field: ClassVar[UUIDField]

class TransactionNow(Func):
    output_field: ClassVar[DateTimeField]
