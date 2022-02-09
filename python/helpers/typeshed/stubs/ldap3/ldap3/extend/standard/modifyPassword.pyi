from typing import Any

from ...extend.operation import ExtendedOperation

class ModifyPassword(ExtendedOperation):
    request_name: str
    request_value: Any
    asn1_spec: Any
    response_attribute: str
    def config(self) -> None: ...
    def __init__(
        self,
        connection,
        user: Any | None = ...,
        old_password: Any | None = ...,
        new_password: Any | None = ...,
        hash_algorithm: Any | None = ...,
        salt: Any | None = ...,
        controls: Any | None = ...,
    ) -> None: ...
    def populate_result(self) -> None: ...
