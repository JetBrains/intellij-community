import datetime
from typing import Any

class BoundVar:
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
    DATE: str
    INTERVAL: str
    NCLOB: str
    NUMBER: str
    TIMESTAMP: str
    types: Any

def dsn(settings_dict: dict[str, Any]) -> str: ...
