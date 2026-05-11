from collections.abc import Sequence
from typing import Any, ClassVar

from django.db.models.expressions import BaseExpression, Combinable
from django.db.models.query import _OrderByFieldName
from django.db.models.query_utils import Q
from typing_extensions import override

class OrderableAggMixin:
    allow_order_by: ClassVar[bool]
    def __init__(
        self,
        *expressions: BaseExpression | Combinable | str,
        distinct: bool = False,
        filter: Q | None = None,
        default: Any | None = None,
        ordering: _OrderByFieldName | Sequence[_OrderByFieldName] = ...,
        order_by: _OrderByFieldName | Sequence[_OrderByFieldName] = ...,
        **extra: Any,
    ) -> None: ...
    @override
    def __init_subclass__(cls, *args: Any, **kwargs: Any) -> None: ...
