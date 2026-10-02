from _typeshed import Incomplete

from .api import Request, Response

class AddPartitionsToTxnResponse_v0(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class AddPartitionsToTxnResponse_v1(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class AddPartitionsToTxnResponse_v2(Response):
    API_KEY: int
    API_VERSION: int
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class AddPartitionsToTxnRequest_v0(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AddPartitionsToTxnResponse_v0
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class AddPartitionsToTxnRequest_v1(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AddPartitionsToTxnResponse_v1
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

class AddPartitionsToTxnRequest_v2(Request):
    API_KEY: int
    API_VERSION: int
    RESPONSE_TYPE = AddPartitionsToTxnResponse_v2
    SCHEMA: Incomplete
    ALIASES: dict[str, str]

AddPartitionsToTxnRequest: list[type[Incomplete]]
AddPartitionsToTxnResponse: list[type[Incomplete]]
