import requests.adapters

class BaseHTTPAdapter(requests.adapters.HTTPAdapter):
    def close(self) -> None: ...
