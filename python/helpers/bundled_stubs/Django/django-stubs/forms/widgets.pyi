import datetime
from collections.abc import Iterable, Iterator, Mapping, Sequence
from typing import Any, Literal, Protocol, type_check_only

from django.core.files.base import File
from django.forms.renderers import BaseRenderer
from django.forms.utils import _DataT, _FilesT
from django.utils.choices import _Choices
from django.utils.datastructures import _ListOrTuple
from django.utils.safestring import SafeString
from typing_extensions import Self, TypeAlias

_OptAttrs: TypeAlias = dict[str, Any]

class MediaOrderConflictWarning(RuntimeWarning): ...

class Media:
    def __init__(
        self,
        media: type | None = None,
        css: dict[str, Sequence[str]] | None = None,
        js: Sequence[str] | None = None,
    ) -> None: ...
    def render(self) -> SafeString: ...
    def render_js(self) -> list[SafeString]: ...
    def render_css(self) -> Iterable[SafeString]: ...
    def absolute_path(self, path: str) -> str: ...
    def __getitem__(self, name: str) -> Media: ...
    @staticmethod
    def merge(*lists: Iterable[Any]) -> list[Any]: ...
    def __add__(self, other: Media) -> Media: ...

class MediaDefiningClass(type): ...

class Widget(metaclass=MediaDefiningClass):
    needs_multipart_form: bool
    is_localized: bool
    is_required: bool
    supports_microseconds: bool
    attrs: _OptAttrs
    template_name: str
    def __init__(self, attrs: _OptAttrs | None = None) -> None: ...
    def __deepcopy__(self, memo: dict[int, Any]) -> Self: ...
    @property
    def is_hidden(self) -> bool: ...
    @property
    def media(self) -> Media: ...
    def subwidgets(self, name: str, value: Any, attrs: _OptAttrs | None = None) -> Iterator[dict[str, Any]]: ...
    def format_value(self, value: Any) -> str | None: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...
    def render(
        self, name: str, value: Any, attrs: _OptAttrs | None = None, renderer: BaseRenderer | None = None
    ) -> SafeString: ...
    def build_attrs(self, base_attrs: _OptAttrs, extra_attrs: _OptAttrs | None = None) -> dict[str, Any]: ...
    def value_from_datadict(self, data: _DataT, files: _FilesT, name: str) -> Any: ...
    def value_omitted_from_data(self, data: _DataT, files: _FilesT, name: str) -> bool: ...
    def id_for_label(self, id_: str) -> str: ...
    def use_required_attribute(self, initial: Any) -> bool: ...

class Input(Widget):
    input_type: str
    template_name: str

class TextInput(Input):
    input_type: str
    template_name: str

class NumberInput(Input):
    input_type: str
    template_name: str

class EmailInput(Input):
    input_type: str
    template_name: str

class URLInput(Input):
    input_type: str
    template_name: str

class PasswordInput(Input):
    render_value: bool
    input_type: str
    template_name: str
    def __init__(self, attrs: _OptAttrs | None = None, render_value: bool = False) -> None: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...

class HiddenInput(Input):
    choices: _Choices
    input_type: str
    template_name: str

class MultipleHiddenInput(HiddenInput):
    template_name: str

class FileInput(Input):
    allow_multiple_selected: bool
    input_type: str
    template_name: str
    needs_multipart_form: bool
    def format_value(self, value: Any) -> None: ...
    def value_from_datadict(self, data: _DataT, files: _FilesT, name: str) -> Any: ...
    def value_omitted_from_data(self, data: _DataT, files: _FilesT, name: str) -> bool: ...
    def use_required_attribute(self, initial: Any) -> bool: ...

FILE_INPUT_CONTRADICTION: object

class ClearableFileInput(FileInput):
    clear_checkbox_label: str
    initial_text: str
    input_text: str
    template_name: str
    checked: bool
    def clear_checkbox_name(self, name: str) -> str: ...
    def clear_checkbox_id(self, name: str) -> str: ...
    def is_initial(self, value: File | str | None) -> bool: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...
    def value_from_datadict(self, data: _DataT, files: _FilesT, name: str) -> Any: ...
    def value_omitted_from_data(self, data: _DataT, files: _FilesT, name: str) -> bool: ...

class Textarea(Widget):
    template_name: str
    def __init__(self, attrs: _OptAttrs | None = None) -> None: ...

class DateTimeBaseInput(TextInput):
    format_key: str
    format: str | None
    supports_microseconds: bool
    def __init__(self, attrs: _OptAttrs | None = None, format: str | None = None) -> None: ...

class DateInput(DateTimeBaseInput):
    format_key: str
    template_name: str

class DateTimeInput(DateTimeBaseInput):
    format_key: str
    template_name: str

class TimeInput(DateTimeBaseInput):
    format_key: str
    template_name: str

def boolean_check(v: Any) -> bool: ...
@type_check_only
class _CheckCallable(Protocol):
    def __call__(self, value: Any, /) -> bool: ...

class CheckboxInput(Input):
    check_test: _CheckCallable
    input_type: str
    template_name: str
    def __init__(self, attrs: _OptAttrs | None = None, check_test: _CheckCallable | None = None) -> None: ...

class ChoiceWidget(Widget):
    allow_multiple_selected: bool
    input_type: str | None
    template_name: str
    option_template_name: str | None
    add_id_index: bool
    checked_attribute: Any
    option_inherits_attrs: bool
    choices: _Choices
    def __init__(self, attrs: _OptAttrs | None = None, choices: _Choices = ()) -> None: ...
    def subwidgets(self, name: str, value: Any, attrs: _OptAttrs | None = None) -> Iterator[dict[str, Any]]: ...
    def options(self, name: str, value: list[str], attrs: _OptAttrs | None = None) -> Iterator[dict[str, Any]]: ...
    def optgroups(
        self, name: str, value: list[str], attrs: _OptAttrs | None = None
    ) -> list[tuple[str | None, list[dict[str, Any]], int | None]]: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...
    def create_option(
        self,
        name: str,
        value: Any,
        label: int | str,
        selected: bool,
        index: int,
        subindex: int | None = None,
        attrs: _OptAttrs | None = None,
    ) -> dict[str, Any]: ...
    def id_for_label(self, id_: str, index: str = "0") -> str: ...
    def value_from_datadict(self, data: _DataT, files: _FilesT, name: str) -> Any: ...
    def format_value(self, value: Any) -> list[str]: ...  # type: ignore[override]

class Select(ChoiceWidget):
    input_type: str | None
    template_name: str
    option_template_name: str
    add_id_index: bool
    checked_attribute: Any
    option_inherits_attrs: bool
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...
    def use_required_attribute(self, initial: Any) -> bool: ...

class NullBooleanSelect(Select):
    def __init__(self, attrs: _OptAttrs | None = None) -> None: ...
    def format_value(self, value: Any) -> str: ...  # type: ignore[override]
    def value_from_datadict(self, data: _DataT, files: _FilesT, name: str) -> bool | None: ...

class SelectMultiple(Select):
    allow_multiple_selected: bool
    def value_from_datadict(self, data: _DataT, files: _FilesT, name: str) -> Any: ...
    def value_omitted_from_data(self, data: _DataT, files: _FilesT, name: str) -> bool: ...

class RadioSelect(ChoiceWidget):
    can_add_related: bool
    input_type: str
    template_name: str
    option_template_name: str

class CheckboxSelectMultiple(ChoiceWidget):
    can_add_related: bool
    input_type: str
    template_name: str
    option_template_name: str
    def use_required_attribute(self, initial: Any) -> bool: ...
    def value_omitted_from_data(self, data: _DataT, files: _FilesT, name: str) -> bool: ...
    def id_for_label(self, id_: str, index: str | None = None) -> str: ...

class MultiWidget(Widget):
    template_name: str
    widgets: Sequence[Widget]
    def __init__(
        self,
        widgets: dict[str, Widget | type[Widget]] | Sequence[Widget | type[Widget]],
        attrs: _OptAttrs | None = None,
    ) -> None: ...
    @property
    def is_hidden(self) -> bool: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...
    def id_for_label(self, id_: str) -> str: ...
    def value_from_datadict(self, data: _DataT, files: _FilesT, name: str) -> list[Any]: ...
    def value_omitted_from_data(self, data: _DataT, files: _FilesT, name: str) -> bool: ...
    def decompress(self, value: Any) -> Any | None: ...
    @property
    def needs_multipart_form(self) -> bool: ...  # type: ignore[override]

class SplitDateTimeWidget(MultiWidget):
    supports_microseconds: bool
    template_name: str
    widgets: tuple[DateInput, TimeInput]
    def __init__(
        self,
        attrs: _OptAttrs | None = None,
        date_format: str | None = None,
        time_format: str | None = None,
        date_attrs: dict[str, str] | None = None,
        time_attrs: dict[str, str] | None = None,
    ) -> None: ...
    def decompress(self, value: Any) -> tuple[datetime.date | None, datetime.time | None]: ...

class SplitHiddenDateTimeWidget(SplitDateTimeWidget):
    template_name: str
    def __init__(
        self,
        attrs: _OptAttrs | None = None,
        date_format: str | None = None,
        time_format: str | None = None,
        date_attrs: dict[str, str] | None = None,
        time_attrs: dict[str, str] | None = None,
    ) -> None: ...

class SelectDateWidget(Widget):
    none_value: tuple[Literal[""], str]
    month_field: str
    day_field: str
    year_field: str
    template_name: str
    input_type: str
    select_widget: type[ChoiceWidget]
    date_re: Any
    years: Iterable[int | str]
    months: Mapping[int, str]
    year_none_value: tuple[Literal[""], str]
    month_none_value: tuple[Literal[""], str]
    day_none_value: tuple[Literal[""], str]
    def __init__(
        self,
        attrs: _OptAttrs | None = None,
        years: Iterable[int | str] | None = None,
        months: Mapping[int, str] | None = None,
        empty_label: str | _ListOrTuple[str] | None = None,
    ) -> None: ...
    def get_context(self, name: str, value: Any, attrs: _OptAttrs | None) -> dict[str, Any]: ...
    def format_value(self, value: Any) -> dict[str, str | int | None]: ...  # type: ignore[override]
    def id_for_label(self, id_: str) -> str: ...
    def value_from_datadict(self, data: _DataT, files: _FilesT, name: str) -> str | None | Any: ...
    def value_omitted_from_data(self, data: _DataT, files: _FilesT, name: str) -> bool: ...
