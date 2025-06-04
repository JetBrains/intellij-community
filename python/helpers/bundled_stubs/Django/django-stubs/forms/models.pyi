from collections.abc import Callable, Collection, Container, Iterator, Mapping, Sequence
from typing import Any, ClassVar, Generic, Literal, TypeVar, overload
from uuid import UUID

from django.db import models
from django.db.models import ForeignKey
from django.db.models.base import Model
from django.db.models.fields import _AllLimitChoicesTo, _LimitChoicesTo
from django.db.models.manager import Manager
from django.db.models.query import QuerySet
from django.forms.fields import ChoiceField, Field, _ClassLevelWidgetT
from django.forms.forms import BaseForm, DeclarativeFieldsMetaclass
from django.forms.formsets import BaseFormSet
from django.forms.renderers import BaseRenderer
from django.forms.utils import ErrorList, _DataT, _FilesT
from django.forms.widgets import Widget
from django.utils.choices import BaseChoiceIterator, CallableChoiceIterator, _Choices, _ChoicesCallable
from django.utils.datastructures import _PropertyDescriptor
from django.utils.functional import _StrOrPromise
from typing_extensions import TypeAlias

ALL_FIELDS: Literal["__all__"]

_Fields: TypeAlias = Collection[str] | Literal["__all__"]
_Widgets: TypeAlias = dict[str, type[Widget] | Widget]

_Labels: TypeAlias = dict[str, str]
_HelpTexts: TypeAlias = dict[str, str]
_ErrorMessages: TypeAlias = dict[str, dict[str, str]]
_FormFieldCallback: TypeAlias = Callable[[models.Field], Field]

_M = TypeVar("_M", bound=Model)
_ParentM = TypeVar("_ParentM", bound=Model)

def construct_instance(
    form: BaseForm, instance: _M, fields: Container[str] | None = None, exclude: Container[str] | None = None
) -> _M: ...
def model_to_dict(instance: Model, fields: _Fields | None = None, exclude: _Fields | None = None) -> dict[str, Any]: ...
def apply_limit_choices_to_to_formfield(formfield: Field) -> None: ...
def fields_for_model(
    model: type[Model],
    fields: _Fields | None = None,
    exclude: _Fields | None = None,
    widgets: _Widgets | None = None,
    formfield_callback: _FormFieldCallback | None = None,
    localized_fields: _Fields | None = None,
    labels: _Labels | None = None,
    help_texts: _HelpTexts | None = None,
    error_messages: _ErrorMessages | None = None,
    field_classes: Mapping[str, type[Field]] | None = None,
    *,
    apply_limit_choices_to: bool = True,
    form_declared_fields: _Fields | None = None,
) -> dict[str, Any]: ...

class ModelFormOptions(Generic[_M]):
    model: type[_M]
    fields: _Fields | None
    exclude: _Fields | None
    widgets: _Widgets | None
    localized_fields: _Fields | None
    labels: _Labels | None
    help_texts: _HelpTexts | None
    error_messages: _ErrorMessages | None
    field_classes: dict[str, type[Field]] | None
    formfield_callback: _FormFieldCallback | None
    def __init__(self, options: type | None = None) -> None: ...

class ModelFormMetaclass(DeclarativeFieldsMetaclass): ...

class BaseModelForm(Generic[_M], BaseForm):
    instance: _M
    _meta: ModelFormOptions[_M]
    def __init__(
        self,
        data: _DataT | None = None,
        files: _FilesT | None = None,
        auto_id: bool | str = "id_%s",
        prefix: str | None = None,
        initial: Mapping[str, Any] | None = None,
        error_class: type[ErrorList] = ...,
        label_suffix: str | None = None,
        empty_permitted: bool = False,
        instance: _M | None = None,
        use_required_attribute: bool | None = None,
        renderer: BaseRenderer | None = None,
    ) -> None: ...
    def validate_unique(self) -> None: ...
    def save(self, commit: bool = True) -> _M: ...
    def save_m2m(self) -> None: ...

class ModelForm(BaseModelForm[_M], metaclass=ModelFormMetaclass):
    base_fields: ClassVar[dict[str, Field]]

def modelform_factory(
    model: type[_M],
    form: type[ModelForm[_M]] = ...,
    fields: _Fields | None = None,
    exclude: _Fields | None = None,
    formfield_callback: _FormFieldCallback | None = None,
    widgets: _Widgets | None = None,
    localized_fields: _Fields | None = None,
    labels: _Labels | None = None,
    help_texts: _HelpTexts | None = None,
    error_messages: _ErrorMessages | None = None,
    field_classes: Mapping[str, type[Field]] | None = None,
) -> type[ModelForm[_M]]: ...

_ModelFormT = TypeVar("_ModelFormT", bound=ModelForm)

class BaseModelFormSet(Generic[_M, _ModelFormT], BaseFormSet[_ModelFormT]):
    model: type[_M]
    edit_only: bool
    unique_fields: Collection[str]
    queryset: QuerySet[_M] | None
    initial_extra: Sequence[dict[str, Any]] | None
    def __init__(
        self,
        data: _DataT | None = None,
        files: _FilesT | None = None,
        auto_id: str = "id_%s",
        prefix: str | None = None,
        queryset: QuerySet[_M] | None = None,
        *,
        initial: Sequence[dict[str, Any]] | None = None,
        **kwargs: Any,
    ) -> None: ...
    def initial_form_count(self) -> int: ...
    def get_queryset(self) -> QuerySet[_M]: ...
    def save_new(self, form: _ModelFormT, commit: bool = True) -> _M: ...
    def save_existing(self, form: _ModelFormT, obj: _M, commit: bool = True) -> _M: ...
    def delete_existing(self, obj: _M, commit: bool = True) -> None: ...
    saved_forms: list[_ModelFormT]
    def save_m2m(self) -> None: ...
    def save(self, commit: bool = True) -> list[_M]: ...
    def clean(self) -> None: ...
    def validate_unique(self) -> None: ...
    def get_unique_error_message(self, unique_check: Sequence[str]) -> str: ...
    def get_date_error_message(self, date_check: tuple[str, Literal["date", "year", "month"], str, str]) -> str: ...
    def get_form_error(self) -> str: ...
    changed_objects: list[tuple[_M, list[str]]]
    deleted_objects: list[_M]
    def save_existing_objects(self, commit: bool = True) -> list[_M]: ...
    new_objects: list[_M]
    def save_new_objects(self, commit: bool = True) -> list[_M]: ...
    def add_fields(self, form: _ModelFormT, index: int | None) -> None: ...

def modelformset_factory(
    model: type[_M],
    form: type[_ModelFormT] = ...,
    formfield_callback: _FormFieldCallback | None = None,
    formset: type[BaseModelFormSet] = ...,
    extra: int = 1,
    can_delete: bool = False,
    can_order: bool = False,
    max_num: int | None = None,
    fields: _Fields | None = None,
    exclude: _Fields | None = None,
    widgets: _Widgets | None = None,
    validate_max: bool = False,
    localized_fields: _Fields | None = None,
    labels: _Labels | None = None,
    help_texts: _HelpTexts | None = None,
    error_messages: _ErrorMessages | None = None,
    min_num: int | None = None,
    validate_min: bool = False,
    field_classes: Mapping[str, type[Field]] | None = None,
    absolute_max: int | None = None,
    can_delete_extra: bool = True,
    renderer: BaseRenderer | None = None,
    edit_only: bool = False,
) -> type[BaseModelFormSet[_M, _ModelFormT]]: ...

class BaseInlineFormSet(Generic[_M, _ParentM, _ModelFormT], BaseModelFormSet[_M, _ModelFormT]):
    instance: _ParentM
    save_as_new: bool
    unique_fields: Collection[str]
    fk: ForeignKey  # set by inlineformset_set
    def __init__(
        self,
        data: _DataT | None = None,
        files: _FilesT | None = None,
        instance: _ParentM | None = None,
        save_as_new: bool = False,
        prefix: str | None = None,
        queryset: QuerySet[_M] | None = None,
        **kwargs: Any,
    ) -> None: ...
    def initial_form_count(self) -> int: ...
    @classmethod
    def get_default_prefix(cls) -> str: ...
    def save_new(self, form: _ModelFormT, commit: bool = True) -> _M: ...
    def add_fields(self, form: _ModelFormT, index: int | None) -> None: ...
    def get_unique_error_message(self, unique_check: Sequence[str]) -> str: ...

def inlineformset_factory(
    parent_model: type[_ParentM],
    model: type[_M],
    form: type[_ModelFormT] = ...,
    formset: type[BaseInlineFormSet] = ...,
    fk_name: str | None = None,
    fields: _Fields | None = None,
    exclude: _Fields | None = None,
    extra: int = 3,
    can_order: bool = False,
    can_delete: bool = True,
    max_num: int | None = None,
    formfield_callback: _FormFieldCallback | None = None,
    widgets: _Widgets | None = None,
    validate_max: bool = False,
    localized_fields: Sequence[str] | None = None,
    labels: _Labels | None = None,
    help_texts: _HelpTexts | None = None,
    error_messages: _ErrorMessages | None = None,
    min_num: int | None = None,
    validate_min: bool = False,
    field_classes: Mapping[str, type[Field]] | None = None,
    absolute_max: int | None = None,
    can_delete_extra: bool = True,
    renderer: BaseRenderer | None = None,
    edit_only: bool = False,
) -> type[BaseInlineFormSet[_M, _ParentM, _ModelFormT]]: ...

class InlineForeignKeyField(Field):
    disabled: bool
    help_text: _StrOrPromise
    required: bool
    show_hidden_initial: bool
    widget: _ClassLevelWidgetT
    parent_instance: Model
    pk_field: bool
    to_field: str | None
    def __init__(
        self,
        parent_instance: Model,
        *args: Any,
        pk_field: bool = False,
        to_field: str | None = None,
        **kwargs: Any,
    ) -> None: ...
    def clean(self, value: Any) -> Model: ...
    def has_changed(self, initial: Any, data: Any) -> bool: ...

class ModelChoiceIteratorValue:
    def __init__(self, value: Any, instance: Model) -> None: ...

class ModelChoiceIterator(BaseChoiceIterator):
    field: ModelChoiceField
    queryset: QuerySet
    def __init__(self, field: ModelChoiceField) -> None: ...
    def __iter__(self) -> Iterator[tuple[ModelChoiceIteratorValue | str, str]]: ...
    def __len__(self) -> int: ...
    def __bool__(self) -> bool: ...
    def choice(self, obj: Model) -> tuple[ModelChoiceIteratorValue, str]: ...

class ModelChoiceField(ChoiceField, Generic[_M]):
    disabled: bool
    help_text: _StrOrPromise
    required: bool
    show_hidden_initial: bool
    validators: list[Any]
    iterator: type[ModelChoiceIterator]
    empty_label: _StrOrPromise | None
    queryset: QuerySet[_M] | None
    limit_choices_to: _AllLimitChoicesTo | None
    to_field_name: str | None
    def __init__(
        self,
        queryset: Manager[_M] | QuerySet[_M] | None,
        *,
        empty_label: _StrOrPromise | None = "---------",
        required: bool = True,
        widget: Widget | type[Widget] | None = None,
        label: _StrOrPromise | None = None,
        initial: Any | None = None,
        help_text: _StrOrPromise = "",
        to_field_name: str | None = None,
        limit_choices_to: _AllLimitChoicesTo | None = None,
        blank: bool = False,
        **kwargs: Any,
    ) -> None: ...
    def get_limit_choices_to(self) -> _LimitChoicesTo: ...
    def label_from_instance(self, obj: _M) -> str: ...
    choices: _PropertyDescriptor[
        _Choices | _ChoicesCallable | CallableChoiceIterator,
        _Choices | CallableChoiceIterator | ModelChoiceIterator,
    ]
    def prepare_value(self, value: Any) -> Any: ...
    def to_python(self, value: Any | None) -> _M | None: ...
    def validate(self, value: _M | None) -> None: ...
    def has_changed(self, initial: Model | int | str | UUID | None, data: int | str | None) -> bool: ...

class ModelMultipleChoiceField(ModelChoiceField[_M]):
    disabled: bool
    empty_label: _StrOrPromise | None
    help_text: _StrOrPromise
    required: bool
    show_hidden_initial: bool
    widget: _ClassLevelWidgetT
    hidden_widget: type[Widget]
    def __init__(self, queryset: Manager[_M] | QuerySet[_M] | None, **kwargs: Any) -> None: ...
    def to_python(self, value: Any) -> list[_M]: ...  # type: ignore[override]
    def clean(self, value: Any) -> QuerySet[_M]: ...
    def prepare_value(self, value: Any) -> Any: ...
    def has_changed(self, initial: Collection[Any] | None, data: Collection[Any] | None) -> bool: ...  # type: ignore[override]

def modelform_defines_fields(form_class: type[ModelForm]) -> bool: ...
@overload
def _get_foreign_key(
    parent_model: type[Model], model: type[Model], fk_name: str | None = None, can_fail: Literal[True] = True
) -> ForeignKey | None: ...
@overload
def _get_foreign_key(
    parent_model: type[Model], model: type[Model], fk_name: str | None = None, can_fail: Literal[False] = False
) -> ForeignKey: ...
