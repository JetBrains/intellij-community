# Stubs for requests.auth (Python 3)

from typing import Any, Text, Union
from . import compat
from . import cookies
from . import utils
from . import status_codes

extract_cookies_to_jar = cookies.extract_cookies_to_jar
parse_dict_header = utils.parse_dict_header
to_native_string = utils.to_native_string
codes = status_codes.codes

CONTENT_TYPE_FORM_URLENCODED = ...  # type: Any
CONTENT_TYPE_MULTI_PART = ...  # type: Any

def _basic_auth_str(username: Union[bytes, Text], password: Union[bytes, Text]) -> str: ...

class AuthBase:
    def __call__(self, r): ...

class HTTPBasicAuth(AuthBase):
    username = ...  # type: Any
    password = ...  # type: Any
    def __init__(self, username, password) -> None: ...
    def __call__(self, r): ...

class HTTPProxyAuth(HTTPBasicAuth):
    def __call__(self, r): ...

class HTTPDigestAuth(AuthBase):
    username = ...  # type: Any
    password = ...  # type: Any
    last_nonce = ...  # type: Any
    nonce_count = ...  # type: Any
    chal = ...  # type: Any
    pos = ...  # type: Any
    num_401_calls = ...  # type: Any
    def __init__(self, username, password) -> None: ...
    def build_digest_header(self, method, url): ...
    def handle_redirect(self, r, **kwargs): ...
    def handle_401(self, r, **kwargs): ...
    def __call__(self, r): ...
