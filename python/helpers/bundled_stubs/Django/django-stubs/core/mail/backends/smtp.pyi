import smtplib
import ssl
import threading
from typing import Any

from _typeshed import StrOrBytesPath
from django.core.mail.backends.base import BaseEmailBackend
from django.utils.functional import cached_property

class EmailBackend(BaseEmailBackend):
    host: str
    port: int
    username: str
    password: str
    use_tls: bool
    use_ssl: bool
    timeout: int | None
    ssl_keyfile: StrOrBytesPath | None
    ssl_certfile: StrOrBytesPath | None
    connection: smtplib.SMTP_SSL | smtplib.SMTP | None
    _lock: threading.RLock
    def __init__(
        self,
        host: str | None = None,
        port: int | None = None,
        username: str | None = None,
        password: str | None = None,
        use_tls: bool | None = None,
        fail_silently: bool = False,
        use_ssl: bool | None = None,
        timeout: int | None = None,
        ssl_keyfile: StrOrBytesPath | None = None,
        ssl_certfile: StrOrBytesPath | None = None,
        **kwargs: Any,
    ) -> None: ...
    @property
    def connection_class(self) -> type[smtplib.SMTP_SSL | smtplib.SMTP]: ...
    @cached_property
    def ssl_context(self) -> ssl.SSLContext: ...
    def prep_address(self, address: str, force_ascii: bool = True) -> str: ...
