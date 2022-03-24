from typing import Any

from ..operation import ExtendedOperation

class ReplicaInfo(ExtendedOperation):
    request_name: str
    response_name: str
    request_value: Any
    response_attribute: str
    def config(self) -> None: ...
    def __init__(self, connection, server_dn, partition_dn, controls: Any | None = ...) -> None: ...
    def populate_result(self) -> None: ...
