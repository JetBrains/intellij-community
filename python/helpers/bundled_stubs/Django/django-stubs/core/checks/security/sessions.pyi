from collections.abc import Sequence
from typing import Any

from django.apps.config import AppConfig
from django.core.checks.messages import Warning

def add_session_cookie_message(message: str) -> str: ...

W010: Warning
W011: Warning
W012: Warning

def add_httponly_message(message: str) -> str: ...

W013: Warning
W014: Warning
W015: Warning

def check_session_cookie_secure(app_configs: Sequence[AppConfig] | None, **kwargs: Any) -> Sequence[Warning]: ...
def check_session_cookie_httponly(app_configs: Sequence[AppConfig] | None, **kwargs: Any) -> Sequence[Warning]: ...
