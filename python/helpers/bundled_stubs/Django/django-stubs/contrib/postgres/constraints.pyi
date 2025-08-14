from collections.abc import Iterable, Sequence

from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.models import Deferrable
from django.db.models.base import Model
from django.db.models.constraints import BaseConstraint
from django.db.models.expressions import Combinable
from django.db.models.indexes import IndexExpression
from django.db.models.query_utils import Q
from django.utils.functional import _StrOrPromise

class ExclusionConstraintExpression(IndexExpression): ...

class ExclusionConstraint(BaseConstraint):
    template: str
    expressions: Sequence[tuple[str | Combinable, str]]
    index_type: str
    condition: Q | None
    def __init__(
        self,
        *,
        name: str,
        expressions: Sequence[tuple[str | Combinable, str]],
        index_type: str | None = None,
        condition: Q | None = None,
        deferrable: Deferrable | None = None,
        include: list[str] | tuple[str, ...] | None = None,
        violation_error_code: str | None = None,
        violation_error_message: _StrOrPromise | None = None,
    ) -> None: ...
    def check_supported(self, schema_editor: BaseDatabaseSchemaEditor) -> None: ...
    def validate(
        self, model: type[Model], instance: Model, exclude: Iterable[str] | None = None, using: str = "default"
    ) -> None: ...
