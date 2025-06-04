from collections.abc import Sequence
from typing import Any, MutableMapping

from django.contrib.gis.db.models.lookups import GISLookup
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.sql.compiler import _AsSqlType

class SpatialOperator:
    sql_template: Any
    op: Any
    func: Any
    def __init__(self, op: Any | None = None, func: Any | None = None) -> None: ...
    @property
    def default_template(self) -> Any: ...
    def as_sql(
        self,
        connection: BaseDatabaseWrapper,
        lookup: GISLookup,
        template_params: MutableMapping[str, Any],
        sql_params: Sequence[Any],
    ) -> _AsSqlType: ...
