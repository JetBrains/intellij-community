from _typeshed import Incomplete
from typing_extensions import Never

# Inherits from werkzeug.exceptions.HTTPException
class _HTTPException:
    code: Incomplete
    body: Incomplete
    headers: Incomplete
    def __init__(self, code, body, headers, response=None) -> None: ...
    # Params depends on `werkzeug` package version
    def get_body(self, environ=None, scope=None): ...
    def get_headers(self, environ=None, scope=None): ...

def raise_http_exception(status, body, headers) -> Never: ...
