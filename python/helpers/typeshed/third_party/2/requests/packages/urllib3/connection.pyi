# Stubs for requests.packages.urllib3.connection (Python 3.4)

from typing import Any
from httplib import HTTPException
from . import packages
from . import exceptions
from . import util

class DummyConnection: ...

ConnectTimeoutError = exceptions.ConnectTimeoutError
SystemTimeWarning = exceptions.SystemTimeWarning
SecurityWarning = exceptions.SecurityWarning

port_by_scheme = ...  # type: Any
RECENT_DATE = ...  # type: Any

class HTTPConnection(object):
    default_port = ...  # type: Any
    default_socket_options = ...  # type: Any
    is_verified = ...  # type: Any
    source_address = ...  # type: Any
    socket_options = ...  # type: Any
    def __init__(self, *args, **kw) -> None: ...
    def connect(self): ...

class HTTPSConnection(HTTPConnection):
    default_port = ...  # type: Any
    key_file = ...  # type: Any
    cert_file = ...  # type: Any
    def __init__(self, host, port=..., key_file=..., cert_file=..., strict=..., timeout=..., **kw) -> None: ...
    sock = ...  # type: Any
    def connect(self): ...

class VerifiedHTTPSConnection(HTTPSConnection):
    cert_reqs = ...  # type: Any
    ca_certs = ...  # type: Any
    ssl_version = ...  # type: Any
    assert_fingerprint = ...  # type: Any
    key_file = ...  # type: Any
    cert_file = ...  # type: Any
    assert_hostname = ...  # type: Any
    def set_cert(self, key_file=..., cert_file=..., cert_reqs=..., ca_certs=..., assert_hostname=..., assert_fingerprint=...): ...
    sock = ...  # type: Any
    auto_open = ...  # type: Any
    is_verified = ...  # type: Any
    def connect(self): ...

UnverifiedHTTPSConnection = ...  # type: Any

class ConnectionError(Exception): pass
