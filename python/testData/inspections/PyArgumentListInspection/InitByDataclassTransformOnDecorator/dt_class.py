from dt_decorator import my_dt_decorator
from dt_field import DataclassField


@my_dt_decorator(kw_only=True)
class RecordViaDecorator:
    id: int
    name: str = DataclassField()
    address: str | None = DataclassField(default=None)
