from collections.abc import Iterable, Sequence
from typing import Any

from django.utils.functional import _StrOrPromise

from .message import DEFAULT_ATTACHMENT_MIME_TYPE as DEFAULT_ATTACHMENT_MIME_TYPE
from .message import BadHeaderError as BadHeaderError
from .message import EmailMessage as EmailMessage
from .message import EmailMultiAlternatives as EmailMultiAlternatives
from .message import SafeMIMEMultipart as SafeMIMEMultipart
from .message import SafeMIMEText as SafeMIMEText
from .message import forbid_multi_line_headers as forbid_multi_line_headers
from .utils import DNS_NAME as DNS_NAME
from .utils import CachedDnsName as CachedDnsName

def get_connection(backend: str | None = None, fail_silently: bool = False, **kwds: Any) -> Any: ...
def send_mail(
    subject: _StrOrPromise,
    message: _StrOrPromise,
    from_email: str | None,
    recipient_list: Sequence[str],
    fail_silently: bool = False,
    auth_user: str | None = None,
    auth_password: str | None = None,
    connection: Any | None = None,
    html_message: str | None = None,
) -> int: ...
def send_mass_mail(
    datatuple: Iterable[tuple[str, str, str | None, list[str]]],
    fail_silently: bool = False,
    auth_user: str | None = None,
    auth_password: str | None = None,
    connection: Any | None = None,
) -> int: ...
def mail_admins(
    subject: _StrOrPromise,
    message: _StrOrPromise,
    fail_silently: bool = False,
    connection: Any | None = None,
    html_message: str | None = None,
) -> None: ...
def mail_managers(
    subject: _StrOrPromise,
    message: _StrOrPromise,
    fail_silently: bool = False,
    connection: Any | None = None,
    html_message: str | None = None,
) -> None: ...

outbox: list[EmailMessage]
