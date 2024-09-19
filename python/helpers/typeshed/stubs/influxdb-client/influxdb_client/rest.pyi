from _typeshed import Incomplete

from urllib3 import HTTPResponse

from ._sync.rest import RESTResponse
from .client.exceptions import InfluxDBError

class ApiException(InfluxDBError):
    status: Incomplete
    reason: Incomplete
    body: Incomplete
    headers: Incomplete
    def __init__(
        self,
        status: Incomplete | None = None,
        reason: Incomplete | None = None,
        http_resp: HTTPResponse | RESTResponse | None = None,
    ) -> None: ...

class _BaseRESTClient:
    logger: Incomplete
    @staticmethod
    def log_request(method: str, url: str): ...
    @staticmethod
    def log_response(status: str): ...
    @staticmethod
    def log_body(body: object, prefix: str): ...
    @staticmethod
    def log_headers(headers: dict[str, str], prefix: str): ...
