from typing import Any

from django.utils.safestring import SafeString

register: Any

def to_object_display_value(value: Any) -> str: ...
def truncated_unordered_list(value: Any, max_items: int, autoescape: bool = ...) -> SafeString: ...
