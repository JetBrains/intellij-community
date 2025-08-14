from typing import Any

from django.core.exceptions import ValidationError

def prefix_validation_error(error: Any, prefix: Any, code: Any, params: Any) -> ValidationError: ...
