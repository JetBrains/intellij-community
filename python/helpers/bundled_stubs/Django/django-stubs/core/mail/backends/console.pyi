import threading
from typing import TextIO

from django.core.mail.backends.base import BaseEmailBackend
from django.core.mail.message import EmailMessage

class EmailBackend(BaseEmailBackend):
    stream: TextIO
    _lock: threading.RLock
    def write_message(self, message: EmailMessage) -> None: ...
