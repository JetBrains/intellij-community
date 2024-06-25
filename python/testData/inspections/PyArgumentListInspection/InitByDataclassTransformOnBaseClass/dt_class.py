from dt_base import DataclassBase


class RecordViaBaseClass(DataclassBase, kw_only=True):
    id: int
    name: str
