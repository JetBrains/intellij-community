from _typeshed import Incomplete

from urllib3 import HTTPResponse

from .._sync.rest import RESTResponse

logger: Incomplete

class InfluxDBError(Exception):
    response: Incomplete
    message: Incomplete
    retry_after: Incomplete
    def __init__(self, response: HTTPResponse | RESTResponse | None = None, message: str | None = None) -> None: ...
