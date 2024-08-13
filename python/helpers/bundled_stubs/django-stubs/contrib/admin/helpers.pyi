from collections.abc import Callable, Iterable, Iterator, Mapping, Sequence
from typing import Any, type_check_only

from django import forms
from django.contrib.admin.options import ModelAdmin
from django.db.models import Model
from django.db.models.fields import AutoField
from django.forms import BaseForm
from django.forms.boundfield import BoundField
from django.forms.models import ModelForm
from django.forms.utils import ErrorDict, ErrorList
from django.forms.widgets import Media, Widget
from django.utils.safestring import SafeString
from typing_extensions import TypedDict

ACTION_CHECKBOX_NAME: str

class ActionForm(forms.Form):
    action: Any
    select_across: Any

@type_check_only
class _PrepopulatedDict(TypedDict):
    field: BoundField
    dependencies: list[BoundField]

class AdminForm:
    prepopulated_fields: list[_PrepopulatedDict]
    model_admin: ModelAdmin | None
    readonly_fields: Sequence[str]
    form: ModelForm
    fieldsets: list[tuple[Any, dict[str, list[str]]]]
    def __init__(
        self,
        form: ModelForm,
        fieldsets: list[tuple[Any, dict[str, list[str]]]],
        prepopulated_fields: Mapping[str, Iterable[str]],
        readonly_fields: Sequence[str] | None = ...,
        model_admin: ModelAdmin | None = ...,
    ) -> None: ...
    def __iter__(self) -> Iterator[Fieldset]: ...
    @property
    def errors(self) -> ErrorDict: ...
    @property
    def non_field_errors(self) -> Callable[[], ErrorList]: ...
    @property
    def media(self) -> Media: ...

class Fieldset:
    form: ModelForm
    classes: str
    description: str | None
    model_admin: ModelAdmin | None
    readonly_fields: Sequence[str]
    def __init__(
        self,
        form: ModelForm,
        name: Any | None = ...,
        readonly_fields: Sequence[str] = ...,
        fields: Sequence[str] = ...,
        classes: Iterable[str] = ...,
        description: str | None = ...,
        model_admin: ModelAdmin | None = ...,
    ) -> None: ...
    @property
    def media(self) -> Media: ...
    def __iter__(self) -> Iterator[Fieldline]: ...

class Fieldline:
    form: ModelForm
    fields: Sequence[str]
    has_visible_field: bool
    model_admin: ModelAdmin | None
    readonly_fields: Sequence[str]
    def __init__(
        self,
        form: ModelForm,
        field: str | Sequence[str],
        readonly_fields: Sequence[str] | None = ...,
        model_admin: ModelAdmin | None = ...,
    ) -> None: ...
    def __iter__(self) -> Iterator[AdminField | AdminReadonlyField]: ...
    def errors(self) -> SafeString: ...

class AdminField:
    field: BoundField
    is_first: bool
    is_checkbox: bool
    is_readonly: bool
    def __init__(self, form: ModelForm, field: str, is_first: bool) -> None: ...
    def label_tag(self) -> SafeString: ...
    def errors(self) -> SafeString: ...

@type_check_only
class _FieldDictT(TypedDict):
    name: str
    label: str
    help_text: str
    field: Callable[[Model], Any] | str

class AdminReadonlyField:
    field: _FieldDictT
    form: ModelForm
    model_admin: ModelAdmin | None
    is_first: bool
    is_checkbox: bool
    is_readonly: bool
    empty_value_display: Any
    def __init__(
        self,
        form: ModelForm,
        field: Callable[[Model], Any] | str,
        is_first: bool,
        model_admin: ModelAdmin | None = ...,
    ) -> None: ...
    def label_tag(self) -> SafeString: ...
    def contents(self) -> SafeString: ...

class InlineAdminFormSet:
    opts: Any
    formset: Any
    fieldsets: Any
    model_admin: ModelAdmin | None
    readonly_fields: Sequence[str]
    prepopulated_fields: dict[str, Any]
    classes: str
    has_add_permission: bool
    has_change_permission: bool
    has_delete_permission: bool
    has_view_permission: bool
    def __init__(
        self,
        inline: Any,
        formset: Any,
        fieldsets: Any,
        prepopulated_fields: dict[str, Any] | None = ...,
        readonly_fields: Sequence[str] | None = ...,
        model_admin: ModelAdmin | None = ...,
        has_add_permission: bool = ...,
        has_change_permission: bool = ...,
        has_delete_permission: bool = ...,
        has_view_permission: bool = ...,
    ) -> None: ...
    def __iter__(self) -> Iterator[InlineAdminForm]: ...
    def fields(self) -> Iterator[dict[str, dict[str, bool] | bool | Widget | str]]: ...
    def inline_formset_data(self) -> str: ...
    @property
    def forms(self) -> list[BaseForm]: ...
    def non_form_errors(self) -> ErrorList: ...
    @property
    def media(self) -> Media: ...

class InlineAdminForm(AdminForm):
    formset: Any
    original: bool | None
    show_url: bool
    absolute_url: str | None
    def __init__(
        self,
        formset: Any,
        form: ModelForm,
        fieldsets: Any,
        prepopulated_fields: Any,
        original: bool | None,
        readonly_fields: Sequence[str] | None = ...,
        model_admin: ModelAdmin | None = ...,
        view_on_site_url: str | None = ...,
    ) -> None: ...
    def __iter__(self) -> Iterator[InlineFieldset]: ...
    def needs_explicit_pk_field(self) -> bool | AutoField: ...
    def pk_field(self) -> AdminField: ...
    def fk_field(self) -> AdminField: ...
    def deletion_field(self) -> AdminField: ...

class InlineFieldset(Fieldset):
    formset: Any
    def __init__(self, formset: Any, *args: Any, **kwargs: Any) -> None: ...
    def __iter__(self) -> Iterator[Fieldline]: ...

class AdminErrorList(forms.utils.ErrorList):
    def __init__(self, form: ModelForm, inline_formsets: Any) -> None: ...
