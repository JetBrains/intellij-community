from collections.abc import Iterable, Sequence

from django.core.checks import CheckMessage
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Deferrable
from django.db.models.base import Model
from django.db.models.constraints import BaseConstraint
from django.db.models.expressions import Combinable
from django.db.models.indexes import IndexExpression
from django.db.models.query_utils import Q
from django.utils.functional import _StrOrPromise
from typing_extensions import override

from .utils import CheckPostgresInstalledMixin

class ExclusionConstraintExpression(IndexExpression): ...

class ExclusionConstraint(CheckPostgresInstalledMixin, BaseConstraint):
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
    @override
    def check(self, model: type[Model], connection: BaseDatabaseWrapper) -> list[CheckMessage]: ...
    @override
    def validate(
        self, model: type[Model], instance: Model, exclude: Iterable[str] | None = None, using: str = "default"
    ) -> None: ...

__all__ = ["ExclusionConstraint"]
