from typing_extensions import LiteralString

from django.template import Library

register: Library

# @register.simple_tag  # commented out for pytype
def compare_values(value1: str, value2: str) -> LiteralString: ...
