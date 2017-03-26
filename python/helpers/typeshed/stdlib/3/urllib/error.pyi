from typing import Any, Dict, Union
from urllib.response import addinfourl

# Stubs for urllib.error

class URLError(IOError):
    reason = ...  # type: Union[str, BaseException]
class HTTPError(URLError, addinfourl):
    code = ...  # type: int
    headers = ...  # type: Dict[str, str]
class ContentTooShortError(URLError): ...
