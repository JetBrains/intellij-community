from typing import Any

from typing_extensions import deprecated

@deprecated(
    "django.utils.itercompat.is_iterable() is deprecated and will be removed in Django 6.0. Use isinstance(..., collections.abc.Iterable) instead."
)
def is_iterable(x: Any) -> bool: ...
