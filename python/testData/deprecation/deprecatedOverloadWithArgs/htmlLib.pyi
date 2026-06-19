from typing import Any, overload
from typing_extensions import deprecated

@overload
@deprecated("Calling format_html() without passing args or kwargs is deprecated.")
def format_html(format_string: str) -> str: ...
@overload
def format_html(format_string: str, *args: Any, **kwargs: Any) -> str: ...