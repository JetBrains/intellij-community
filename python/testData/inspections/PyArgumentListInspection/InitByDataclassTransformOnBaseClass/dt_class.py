from dt_base import DataclassBase
from dt_field import DataclassField


class RecordViaBaseClass(DataclassBase, kw_only=True):
    id: int
    name: str = DataclassField()
    address: str | None = DataclassField(default=None)


