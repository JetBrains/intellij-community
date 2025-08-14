from typing import Any

from django import forms
from django.db.models.fields import _ErrorMessagesDict
from django.forms.fields import _ClassLevelWidgetT

class HStoreField(forms.CharField):
    widget: _ClassLevelWidgetT
    default_error_messages: _ErrorMessagesDict
    def prepare_value(self, value: Any) -> Any: ...
    def to_python(self, value: Any) -> dict[str, str | None]: ...  # type: ignore[override]
    def has_changed(self, initial: Any, data: Any) -> bool: ...
