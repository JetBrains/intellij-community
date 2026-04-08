import datetime
from collections.abc import Callable, Collection, Iterator, Sequence
from decimal import Decimal
from re import Pattern
from typing import Any, ClassVar, Protocol, TypeAlias, type_check_only
from uuid import UUID

from django.core.files import File
from django.core.validators import _ValidatorCallable
from django.db.models.fields import _ErrorMessagesDict, _ErrorMessagesMapping
from django.forms.boundfield import BoundField
from django.forms.forms import BaseForm
from django.forms.widgets import Widget
from django.utils.choices import CallableChoiceIterator, _ChoicesCallable, _ChoicesInput
from django.utils.datastructures import _PropertyDescriptor
from django.utils.functional import _StrOrPromise
from typing_extensions import Self, override

# Problem: attribute `widget` is always of type `Widget` after field instantiation.
# However, on class level it can be set to `Type[Widget]` too.
# If we annotate it as `Widget | Type[Widget]`, every code that uses field
# instances will not typecheck.
# If we annotate it as `Widget`, any widget subclasses that do e.g.
# `widget = Select` will not typecheck.
# `Any` gives too much freedom, but does not create false positives.
_ClassLevelWidgetT: TypeAlias = Any

class Field:
    initial: Any
    label: _StrOrPromise | None
    required: bool
    widget: _ClassLevelWidgetT
    hidden_widget: type[Widget]
    default_validators: list[_ValidatorCallable]
    default_error_messages: ClassVar[_ErrorMessagesDict]
    empty_values: Sequence[Any]
    show_hidden_initial: bool
    help_text: _StrOrPromise
    disabled: bool
    label_suffix: str | None
    localize: bool
    error_messages: _ErrorMessagesDict
    validators: list[_ValidatorCallable]
    bound_field_class: type[BoundField] | None
    def __init__(
        self,
        *,
        required: bool = True,
        widget: Widget | type[Widget] | None = None,
        label: _StrOrPromise | None = None,
        initial: Any | None = None,
        help_text: _StrOrPromise = "",
        error_messages: _ErrorMessagesMapping | None = None,
        show_hidden_initial: bool = False,
        validators: Sequence[_ValidatorCallable] = (),
        localize: bool = False,
        disabled: bool = False,
        label_suffix: str | None = None,
        template_name: str | None = None,
        bound_field_class: type[BoundField] | None = None,
    ) -> None: ...
    def prepare_value(self, value: Any) -> Any: ...
    def to_python(self, value: Any | None) -> Any | None: ...
    def validate(self, value: Any) -> None: ...
    def run_validators(self, value: Any) -> None: ...
    def clean(self, value: Any) -> Any: ...
    def bound_data(self, data: Any, initial: Any) -> Any: ...
    def widget_attrs(self, widget: Widget) -> dict[str, Any]: ...
    def has_changed(self, initial: Any | None, data: Any | None) -> bool: ...
    def get_bound_field(self, form: BaseForm, field_name: str) -> BoundField: ...
    def __deepcopy__(self, memo: dict[int, Any]) -> Self: ...

class CharField(Field):
    max_length: int | None
    min_length: int | None
    strip: bool
    empty_value: str | None
    def __init__(
        self,
        *,
        max_length: int | None = None,
        min_length: int | None = None,
        strip: bool = True,
        empty_value: str | None = "",
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def to_python(self, value: Any | None) -> str | None: ...
    @override
    def widget_attrs(self, widget: Widget) -> dict[str, Any]: ...

class IntegerField(Field):
    widget: _ClassLevelWidgetT
    max_value: int | Callable[[], int] | None
    min_value: int | Callable[[], int] | None
    step_size: int | Callable[[], int] | None
    re_decimal: Any
    def __init__(
        self,
        *,
        max_value: int | Callable[[], int] | None = None,
        min_value: int | Callable[[], int] | None = None,
        step_size: int | Callable[[], int] | None = None,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def to_python(self, value: Any | None) -> int | None: ...
    @override
    def widget_attrs(self, widget: Widget) -> dict[str, Any]: ...

class FloatField(IntegerField):
    def __init__(
        self,
        *,
        max_value: int | float | None = None,
        min_value: int | float | None = None,
        step_size: int | float | None = None,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def to_python(self, value: Any | None) -> float | None: ...  # type: ignore[override]
    @override
    def validate(self, value: float) -> None: ...
    @override
    def widget_attrs(self, widget: Widget) -> dict[str, Any]: ...

class DecimalField(IntegerField):
    decimal_places: int | None
    max_digits: int | None
    def __init__(
        self,
        *,
        max_value: Decimal | int | float | None = None,
        min_value: Decimal | int | float | None = None,
        max_digits: int | None = None,
        decimal_places: int | None = None,
        step_size: Decimal | int | float | None = ...,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def to_python(self, value: Any | None) -> Decimal | None: ...  # type: ignore[override]
    @override
    def validate(self, value: Decimal) -> None: ...
    @override
    def widget_attrs(self, widget: Widget) -> dict[str, Any]: ...

class BaseTemporalField(Field):
    input_formats: Any
    def __init__(
        self,
        *,
        input_formats: Any | None = None,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def to_python(self, value: str | None) -> Any | None: ...
    def strptime(self, value: str, format: str) -> Any: ...

class DateField(BaseTemporalField):
    widget: _ClassLevelWidgetT
    @override
    def to_python(self, value: None | str | datetime.datetime | datetime.date) -> datetime.date | None: ...
    @override
    def strptime(self, value: str, format: str) -> datetime.date: ...

class TimeField(BaseTemporalField):
    widget: _ClassLevelWidgetT
    @override
    def to_python(self, value: None | str | datetime.time) -> datetime.time | None: ...
    @override
    def strptime(self, value: str, format: str) -> datetime.time: ...

class DateTimeFormatsIterator:
    def __iter__(self) -> Iterator[str]: ...

class DateTimeField(BaseTemporalField):
    widget: _ClassLevelWidgetT
    @override
    def prepare_value(self, value: Any) -> Any: ...
    @override
    def to_python(self, value: None | str | datetime.datetime | datetime.date) -> datetime.datetime | None: ...
    @override
    def strptime(self, value: str, format: str) -> datetime.datetime: ...

class DurationField(Field):
    @override
    def prepare_value(self, value: datetime.timedelta | str | None) -> str | None: ...
    @override
    def to_python(self, value: Any | None) -> datetime.timedelta | None: ...

class RegexField(CharField):
    regex: _PropertyDescriptor[str | Pattern[str], Pattern[str]]
    def __init__(
        self,
        regex: str | Pattern[str],
        *,
        max_length: int | None = ...,
        min_length: int | None = ...,
        strip: bool = ...,
        empty_value: str | None = ...,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...

class EmailField(CharField):
    widget: _ClassLevelWidgetT
    def __init__(
        self,
        *,
        max_length: int | None = ...,
        min_length: int | None = ...,
        strip: bool = ...,
        empty_value: str | None = ...,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...

class FileField(Field):
    widget: _ClassLevelWidgetT
    allow_empty_file: bool
    max_length: int | None
    def __init__(
        self,
        *,
        max_length: int | None = None,
        allow_empty_file: bool = False,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def to_python(self, data: File | None) -> File | None: ...
    @override
    def clean(self, data: Any, initial: Any | None = None) -> Any: ...
    @override
    def bound_data(self, _: Any | None, initial: Any) -> Any: ...
    @override
    def has_changed(self, initial: Any | None, data: Any | None) -> bool: ...

class ImageField(FileField):
    @override
    def to_python(self, data: File | None) -> File | None: ...
    @override
    def widget_attrs(self, widget: Widget) -> dict[str, Any]: ...

class URLField(CharField):
    widget: _ClassLevelWidgetT
    def __init__(
        self,
        *,
        max_length: int | None = ...,
        min_length: int | None = ...,
        strip: bool = ...,
        empty_value: str | None = ...,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
        assume_scheme: str | None = None,
    ) -> None: ...
    @override
    def to_python(self, value: Any | None) -> str | None: ...

class BooleanField(Field):
    widget: _ClassLevelWidgetT
    @override
    def to_python(self, value: Any | None) -> bool: ...
    @override
    def validate(self, value: Any) -> None: ...
    @override
    def has_changed(self, initial: Any | None, data: Any | None) -> bool: ...

class NullBooleanField(BooleanField):
    widget: _ClassLevelWidgetT
    @override
    def to_python(self, value: Any | None) -> bool | None: ...  # type: ignore[override]
    @override
    def validate(self, value: Any) -> None: ...

class ChoiceField(Field):
    choices: _PropertyDescriptor[
        _ChoicesInput | _ChoicesCallable | CallableChoiceIterator,
        _ChoicesInput | CallableChoiceIterator,
    ]
    widget: _ClassLevelWidgetT
    def __init__(
        self,
        *,
        choices: _ChoicesInput | _ChoicesCallable = (),
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def __deepcopy__(self, memo: dict[int, Any]) -> Self: ...
    # Real return type of `to_python` is `str`, but it results in errors when
    # subclassing `ModelChoiceField`: `# type: ignore[override]` is not inherited
    @override
    def to_python(self, value: Any | None) -> Any: ...
    @override
    def validate(self, value: Any) -> None: ...
    def valid_value(self, value: Any) -> bool: ...

@type_check_only
class _CoerceCallable(Protocol):
    def __call__(self, value: Any, /) -> Any: ...

class TypedChoiceField(ChoiceField):
    coerce: _CoerceCallable
    empty_value: str | None
    def __init__(
        self,
        *,
        coerce: _CoerceCallable = ...,
        empty_value: str | None = "",
        choices: _ChoicesInput | _ChoicesCallable = ...,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def clean(self, value: Any) -> Any: ...

class MultipleChoiceField(ChoiceField):
    widget: _ClassLevelWidgetT
    hidden_widget: type[Widget]
    @override
    def to_python(self, value: Any | None) -> list[str]: ...
    @override
    def validate(self, value: Any) -> None: ...
    @override
    def has_changed(self, initial: Collection[Any] | None, data: Collection[Any] | None) -> bool: ...

class TypedMultipleChoiceField(MultipleChoiceField):
    coerce: _CoerceCallable
    empty_value: list[Any] | None
    def __init__(
        self,
        *,
        coerce: _CoerceCallable = ...,
        empty_value: list[Any] | None = ...,
        choices: _ChoicesInput | _ChoicesCallable = ...,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def clean(self, value: Any) -> Any: ...
    @override
    def validate(self, value: Any) -> None: ...

class ComboField(Field):
    fields: Sequence[Field]
    def __init__(
        self,
        fields: Sequence[Field],
        *,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def clean(self, value: Any) -> Any: ...

class MultiValueField(Field):
    require_all_fields: bool
    fields: Sequence[Field]
    def __init__(
        self,
        fields: Sequence[Field],
        *,
        require_all_fields: bool = True,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def __deepcopy__(self, memo: dict[int, Any]) -> Self: ...
    @override
    def validate(self, value: Any) -> None: ...
    @override
    def clean(self, value: Any) -> Any: ...
    def compress(self, data_list: Any) -> Any: ...
    @override
    def has_changed(self, initial: Any | None, data: Any | None) -> bool: ...

class FilePathField(ChoiceField):
    allow_files: bool
    allow_folders: bool
    match: str | None
    path: str
    recursive: bool
    match_re: Pattern[str] | None
    def __init__(
        self,
        path: str,
        *,
        match: str | None = None,
        recursive: bool = False,
        allow_files: bool = True,
        allow_folders: bool = False,
        choices: _ChoicesInput | _ChoicesCallable = ...,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...

class SplitDateTimeField(MultiValueField):
    widget: _ClassLevelWidgetT
    hidden_widget: type[Widget]
    def __init__(
        self,
        *,
        input_date_formats: Any | None = None,
        input_time_formats: Any | None = None,
        fields: Sequence[Field] = ...,
        require_all_fields: bool = ...,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def compress(self, data_list: tuple[datetime.date, datetime.time] | None) -> datetime.datetime | None: ...

class GenericIPAddressField(CharField):
    unpack_ipv4: bool
    def __init__(
        self,
        *,
        protocol: str = "both",
        unpack_ipv4: bool = False,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...
    @override
    def to_python(self, value: Any) -> str: ...

class SlugField(CharField):
    allow_unicode: bool
    def __init__(
        self,
        *,
        allow_unicode: bool = False,
        max_length: Any | None = ...,
        min_length: Any | None = ...,
        strip: bool = ...,
        empty_value: str | None = ...,
        required: bool = ...,
        widget: Widget | type[Widget] | None = ...,
        label: _StrOrPromise | None = ...,
        initial: Any | None = ...,
        help_text: _StrOrPromise = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
        show_hidden_initial: bool = ...,
        validators: Sequence[_ValidatorCallable] = ...,
        localize: bool = ...,
        disabled: bool = ...,
        label_suffix: str | None = ...,
    ) -> None: ...

class UUIDField(CharField):
    @override
    def prepare_value(self, value: Any | None) -> Any | None: ...
    @override
    def to_python(self, value: Any) -> UUID | None: ...  # type: ignore[override]

class InvalidJSONInput(str): ...
class JSONString(str): ...

class JSONField(CharField):
    default_error_messages: ClassVar[_ErrorMessagesDict]
    widget: _ClassLevelWidgetT
    encoder: Any
    decoder: Any
    def __init__(self, encoder: Any | None = None, decoder: Any | None = None, **kwargs: Any) -> None: ...
    @override
    def to_python(self, value: Any) -> Any: ...
    @override
    def bound_data(self, data: Any, initial: Any) -> Any: ...
    @override
    def prepare_value(self, value: Any) -> str: ...
    @override
    def has_changed(self, initial: Any | None, data: Any | None) -> bool: ...

__all__ = (
    "BooleanField",
    "CharField",
    "ChoiceField",
    "ComboField",
    "DateField",
    "DateTimeField",
    "DecimalField",
    "DurationField",
    "EmailField",
    "Field",
    "FileField",
    "FilePathField",
    "FloatField",
    "GenericIPAddressField",
    "ImageField",
    "IntegerField",
    "JSONField",
    "MultiValueField",
    "MultipleChoiceField",
    "NullBooleanField",
    "RegexField",
    "SlugField",
    "SplitDateTimeField",
    "TimeField",
    "TypedChoiceField",
    "TypedMultipleChoiceField",
    "URLField",
    "UUIDField",
)
