from collections.abc import Sequence
from typing import Any, ClassVar

from django import forms
from django.db.models.fields import _ErrorMessagesDict
from django.forms.fields import _ClassLevelWidgetT
from django.forms.utils import _DataT, _FilesT
from django.forms.widgets import _OptAttrs
from typing_extensions import override

from ..utils import prefix_validation_error as prefix_validation_error

class SimpleArrayField(forms.CharField):
    default_error_messages: ClassVar[_ErrorMessagesDict]
    base_field: forms.Field
    delimiter: str
    min_length: int | None
    max_length: int | None
    def __init__(
        self,
        base_field: forms.Field,
        *,
        delimiter: str = ",",
        max_length: int | None = None,
        min_length: int | None = None,
        **kwargs: Any,
    ) -> None: ...
    @override
    def clean(self, value: Any) -> Sequence[Any]: ...
    @override
    def prepare_value(self, value: Any) -> Any: ...
    @override
    def to_python(self, value: Any) -> Sequence[Any]: ...  # type: ignore[override]
    @override
    def validate(self, value: Sequence[Any]) -> None: ...
    @override
    def run_validators(self, value: Sequence[Any]) -> None: ...
    @override
    def has_changed(self, initial: Any, data: Any) -> bool: ...

class SplitArrayWidget(forms.Widget):
    template_name: str
    widget: _ClassLevelWidgetT
    size: int
    def __init__(self, widget: forms.Widget | type[forms.Widget], size: int, **kwargs: Any) -> None: ...
    @property
    @override
    def is_hidden(self) -> bool: ...
    @override
    def value_from_datadict(self, data: _DataT, files: _FilesT, name: str) -> Any: ...
    @override
    def value_omitted_from_data(self, data: _DataT, files: _FilesT, name: str) -> bool: ...
    @override
    def id_for_label(self, id_: str) -> str: ...
    @override
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None = None) -> dict[str, Any]: ...
    @property
    @override
    def needs_multipart_form(self) -> bool: ...  # type: ignore[override]

class SplitArrayField(forms.Field):
    default_error_messages: ClassVar[_ErrorMessagesDict]
    base_field: forms.Field
    size: int
    remove_trailing_nulls: bool
    def __init__(
        self, base_field: forms.Field, size: int, *, remove_trailing_nulls: bool = False, **kwargs: Any
    ) -> None: ...
    @override
    def to_python(self, value: Any) -> Sequence[Any]: ...
    @override
    def clean(self, value: Any) -> Sequence[Any]: ...
    @override
    def has_changed(self, initial: Any, data: Any) -> bool: ...
