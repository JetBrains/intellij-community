from typing import Any

from django.forms import NullBooleanField
from typing_extensions import override

class StrictBooleanField(NullBooleanField):
    @override
    def to_python(self, value: Any | None) -> bool: ...

class StrictNullBooleanField(NullBooleanField): ...
