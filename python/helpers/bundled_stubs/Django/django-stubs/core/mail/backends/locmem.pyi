from collections.abc import Sequence

from django.core.mail.backends.base import BaseEmailBackend
from django.core.mail.message import EmailMessage
from typing_extensions import override

class EmailBackend(BaseEmailBackend):
    @override
    def send_messages(self, messages: Sequence[EmailMessage]) -> int: ...
