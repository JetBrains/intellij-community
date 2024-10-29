from dt_base import DataclassBase


class RecordViaMetaClass(DataclassBase, kw_only=True):
    id: int
    name: str
