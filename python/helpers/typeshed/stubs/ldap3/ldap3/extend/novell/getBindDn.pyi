from typing import Any

from ...extend.operation import ExtendedOperation

class GetBindDn(ExtendedOperation):
    request_name: str
    response_name: str
    response_attribute: str
    asn1_spec: Any
    def config(self) -> None: ...
    def populate_result(self) -> None: ...
