from typing import Any, ClassVar

from django import forms
from django.db.models.fields import _ErrorMessagesDict
from django.forms.fields import _ClassLevelWidgetT
from typing_extensions import override

class HStoreField(forms.CharField):
    widget: _ClassLevelWidgetT
    default_error_messages: ClassVar[_ErrorMessagesDict]
    @override
    def prepare_value(self, value: Any) -> Any: ...
    @override
    def to_python(self, value: Any) -> dict[str, str | None]: ...  # type: ignore[override]
    @override
    def has_changed(self, initial: Any, data: Any) -> bool: ...

__all__ = ["HStoreField"]
