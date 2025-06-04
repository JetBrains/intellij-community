from collections.abc import Sequence
from typing import Any

from django.apps.config import AppConfig
from django.core.checks.messages import Error, Warning

W003: Warning
W016: Warning

def check_csrf_middleware(app_configs: Sequence[AppConfig] | None, **kwargs: Any) -> Sequence[Warning]: ...
def check_csrf_cookie_secure(app_configs: Sequence[AppConfig] | None, **kwargs: Any) -> Sequence[Warning]: ...
def check_csrf_failure_view(app_configs: Sequence[AppConfig] | None, **kwargs: Any) -> Sequence[Error]: ...
