from typing import Any, ClassVar

from django.db import models
from django.db.models.expressions import Func

class CumeDist(Func):
    output_field: ClassVar[models.FloatField]

class DenseRank(Func):
    output_field: ClassVar[models.IntegerField]

class FirstValue(Func): ...

class LagLeadFunction(Func):
    def __init__(self, expression: str | None, offset: int = 1, default: int | None = None, **extra: Any) -> None: ...

class Lag(LagLeadFunction): ...
class LastValue(Func): ...
class Lead(LagLeadFunction): ...

class NthValue(Func):
    def __init__(self, expression: str | None, nth: int = 1, **extra: Any) -> None: ...

class Ntile(Func):
    def __init__(self, num_buckets: int = 1, **extra: Any) -> None: ...
    output_field: ClassVar[models.IntegerField]

class PercentRank(Func):
    output_field: ClassVar[models.FloatField]

class Rank(Func):
    output_field: ClassVar[models.IntegerField]

class RowNumber(Func):
    output_field: ClassVar[models.IntegerField]
