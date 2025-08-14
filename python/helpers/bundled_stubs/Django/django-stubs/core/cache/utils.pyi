from collections.abc import Iterable
from typing import Any

TEMPLATE_FRAGMENT_KEY_TEMPLATE: str

def make_template_fragment_key(fragment_name: str, vary_on: Iterable[Any] | None = None) -> str: ...
