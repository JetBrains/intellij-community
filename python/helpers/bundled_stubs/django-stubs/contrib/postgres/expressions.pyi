from typing import Any

from django.contrib.postgres.fields import ArrayField
from django.db.models import Subquery
from django.db.models.query import QuerySet
from django.db.models.sql.query import Query
from django.utils.functional import cached_property

class ArraySubquery(Subquery):
    template: str

    def __init__(self, queryset: Query | QuerySet[Any], **kwargs: Any) -> None: ...
    @cached_property
    def output_field(self) -> ArrayField: ...
