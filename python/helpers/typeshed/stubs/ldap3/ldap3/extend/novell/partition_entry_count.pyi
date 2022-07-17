from typing import Any

from ..operation import ExtendedOperation

class PartitionEntryCount(ExtendedOperation):
    request_name: str
    response_name: str
    request_value: Any
    response_attribute: str
    def config(self) -> None: ...
    def __init__(self, connection, partition_dn, controls: Any | None = ...) -> None: ...
    def populate_result(self) -> None: ...
