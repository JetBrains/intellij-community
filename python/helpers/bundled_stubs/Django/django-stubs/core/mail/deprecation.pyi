# RemovedInDjango70Warning: this entire file.

from django.core.mail.backends.base import BaseEmailBackend

FAIL_SILENTLY_ARG_WARNING: str
AUTH_ARGS_WARNING: str
CONNECTION_ARG_WARNING: str
NO_DEFAULT_MAILER_WARNING: str

def report_using_incompatibility(
    connection: BaseEmailBackend | None = None,
    fail_silently: bool = False,
    auth_user: str | None = None,
    auth_password: str | None = None,
) -> None: ...
def warn_about_default_mailers_if_needed() -> None: ...
