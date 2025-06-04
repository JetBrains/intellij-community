import smtplib
import threading

from _typeshed import StrOrBytesPath
from django.core.mail.backends.base import BaseEmailBackend

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
