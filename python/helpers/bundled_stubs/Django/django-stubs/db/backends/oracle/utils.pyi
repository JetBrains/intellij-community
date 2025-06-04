import datetime
from typing import Any

class InsertVar:
    types: Any
    db_type: Any
    bound_param: Any
    def __init__(self, field: Any) -> None: ...
    def bind_parameter(self, cursor: Any) -> Any: ...
    def get_value(self) -> Any: ...

class Oracle_datetime(datetime.datetime):
    input_size: Any
    @classmethod
    def from_datetime(cls, dt: Any) -> Any: ...

class BulkInsertMapper:
    BLOB: str
    CLOB: str
    DATE: str
    INTERVAL: str
    NUMBER: str
    TIMESTAMP: str
    types: Any
