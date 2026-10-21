from django.core.exceptions import ImproperlyConfigured

class InvalidMailer(ImproperlyConfigured):
    def __init__(self, msg: str, *, alias: str | None = None) -> None: ...

class MailerDoesNotExist(InvalidMailer, KeyError):
    def __init__(self, *, alias: str) -> None: ...
