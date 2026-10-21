from collections.abc import Mapping

import requests.adapters
from urllib3.connectionpool import ConnectionPool

class BaseHTTPAdapter(requests.adapters.HTTPAdapter):
    def close(self) -> None: ...
    def get_connection_with_tls_context(
        self,
        request: requests.PreparedRequest,
        verify: bool | str | None,
        proxies: Mapping[str, str] | None = None,
        cert: tuple[str, str] | str | None = None,
    ) -> ConnectionPool: ...
