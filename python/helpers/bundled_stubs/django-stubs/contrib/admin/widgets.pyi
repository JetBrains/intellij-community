from collections.abc import Iterable, Mapping, Sequence
from typing import Any

from django import forms
from django.contrib.admin.sites import AdminSite
from django.core.files.base import File
from django.db.models.fields.reverse_related import ManyToManyRel, ManyToOneRel
from django.forms.models import ModelChoiceIterator
from django.forms.widgets import ChoiceWidget, _OptAttrs
from django.utils.choices import _Choices
from django.utils.functional import _StrOrPromise

class FilteredSelectMultiple(forms.SelectMultiple):
    verbose_name: _StrOrPromise
    is_stacked: bool
    def __init__(
        self,
        verbose_name: _StrOrPromise,
        is_stacked: bool,
        attrs: _OptAttrs | None = ...,
        choices: _Choices = ...,
    ) -> None: ...

class BaseAdminDateWidget(forms.DateInput):
    def __init__(self, attrs: _OptAttrs | None = ..., format: str | None = ...) -> None: ...

class AdminDateWidget(BaseAdminDateWidget):
    template_name: str

class BaseAdminTimeWidget(forms.TimeInput):
    def __init__(self, attrs: _OptAttrs | None = ..., format: str | None = ...) -> None: ...

class AdminTimeWidget(BaseAdminTimeWidget):
    template_name: str

class AdminSplitDateTime(forms.SplitDateTimeWidget):
    template_name: str
    def __init__(self, attrs: _OptAttrs | None = ...) -> None: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...

class AdminRadioSelect(forms.RadioSelect): ...
class AdminFileWidget(forms.ClearableFileInput): ...

def url_params_from_lookup_dict(lookups: Any) -> dict[str, str]: ...

class ForeignKeyRawIdWidget(forms.TextInput):
    rel: ManyToOneRel
    admin_site: AdminSite
    db: str | None
    def __init__(
        self,
        rel: ManyToOneRel,
        admin_site: AdminSite,
        attrs: _OptAttrs | None = ...,
        using: str | None = ...,
    ) -> None: ...
    def base_url_parameters(self) -> dict[str, str]: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...
    def url_parameters(self) -> dict[str, str]: ...
    def label_and_url_for_value(self, value: Any) -> tuple[str, str]: ...

class ManyToManyRawIdWidget(ForeignKeyRawIdWidget):
    rel: ManyToManyRel  # type: ignore[assignment]
    def __init__(
        self,
        rel: ManyToManyRel,
        admin_site: AdminSite,
        attrs: _OptAttrs | None = ...,
        using: str | None = ...,
    ) -> None: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...
    def url_parameters(self) -> dict[str, str]: ...
    def label_and_url_for_value(self, value: Any) -> tuple[str, str]: ...
    def format_value(self, value: Any) -> str | None: ...
    def value_from_datadict(self, data: Mapping[str, Any], files: Mapping[str, Iterable[File]], name: str) -> Any: ...

class RelatedFieldWidgetWrapper(forms.Widget):
    template_name: str
    widget: ChoiceWidget
    rel: ManyToOneRel
    can_add_related: bool
    can_change_related: bool
    can_delete_related: bool
    can_view_related: bool
    admin_site: AdminSite
    def __init__(
        self,
        widget: ChoiceWidget,
        rel: ManyToOneRel,
        admin_site: AdminSite,
        can_add_related: bool | None = ...,
        can_change_related: bool = ...,
        can_delete_related: bool = ...,
        can_view_related: bool = ...,
    ) -> None: ...
    @property
    def is_hidden(self) -> bool: ...
    @property
    def choices(self) -> ModelChoiceIterator: ...
    @choices.setter
    def choices(self, value: ModelChoiceIterator) -> None: ...
    def get_related_url(self, info: tuple[str, str], action: str, *args: Any) -> str: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...
    def value_from_datadict(self, data: Mapping[str, Any], files: Mapping[str, Iterable[File]], name: str) -> Any: ...
    def value_omitted_from_data(
        self, data: Mapping[str, Any], files: Mapping[str, Iterable[File]], name: str
    ) -> bool: ...
    def id_for_label(self, id_: str) -> str: ...

class AdminTextareaWidget(forms.Textarea):
    def __init__(self, attrs: _OptAttrs | None = ...) -> None: ...

class AdminTextInputWidget(forms.TextInput):
    def __init__(self, attrs: _OptAttrs | None = ...) -> None: ...

class AdminEmailInputWidget(forms.EmailInput):
    def __init__(self, attrs: _OptAttrs | None = ...) -> None: ...

class AdminURLFieldWidget(forms.URLInput):
    template_name: str
    def __init__(self, attrs: _OptAttrs | None = ..., validator_class: Any = ...) -> None: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...

class AdminIntegerFieldWidget(forms.NumberInput):
    def __init__(self, attrs: _OptAttrs | None = ...) -> None: ...
    class_name: str

class AdminBigIntegerFieldWidget(AdminIntegerFieldWidget):
    class_name: str

class AdminUUIDInputWidget(forms.TextInput):
    def __init__(self, attrs: _OptAttrs | None = ...) -> None: ...

SELECT2_TRANSLATIONS: dict[str, str]

class AutocompleteMixin:
    url_name: str
    field: Any
    admin_site: AdminSite
    db: str | None
    choices: Any
    attrs: _OptAttrs
    def __init__(
        self,
        field: Any,
        admin_site: AdminSite,
        attrs: _OptAttrs | None = ...,
        choices: Any = ...,
        using: str | None = ...,
    ) -> None: ...
    def get_url(self) -> str: ...
    def build_attrs(self, base_attrs: _OptAttrs, extra_attrs: _OptAttrs | None = ...) -> dict[str, Any]: ...
    # typo in source: `attr` instead of `attrs`
    def optgroups(
        self, name: str, value: Sequence[str], attr: _OptAttrs | None = ...
    ) -> list[tuple[str | None, list[dict[str, Any]], int | None]]: ...

class AutocompleteSelect(AutocompleteMixin, forms.Select): ...
class AutocompleteSelectMultiple(AutocompleteMixin, forms.SelectMultiple): ...
