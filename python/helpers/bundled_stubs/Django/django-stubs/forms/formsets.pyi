from collections.abc import Iterator, Mapping, Sequence, Sized
from typing import Any, Generic, TypeVar

from django.db.models.fields import _ErrorMessagesDict
from django.forms.forms import BaseForm, Form
from django.forms.renderers import BaseRenderer
from django.forms.utils import ErrorList, RenderableFormMixin, _DataT, _FilesT
from django.forms.widgets import Media, MediaDefiningClass, Widget
from django.utils.functional import cached_property

TOTAL_FORM_COUNT: str
INITIAL_FORM_COUNT: str
MIN_NUM_FORM_COUNT: str
MAX_NUM_FORM_COUNT: str
ORDERING_FIELD_NAME: str
DELETION_FIELD_NAME: str

DEFAULT_MIN_NUM: int
DEFAULT_MAX_NUM: int

_F = TypeVar("_F", bound=BaseForm)

class ManagementForm(Form):
    cleaned_data: dict[str, int | None]
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def clean(self) -> dict[str, int | None]: ...

class BaseFormSet(Generic[_F], Sized, RenderableFormMixin):
    form: type[_F]
    extra: int
    can_order: bool
    can_delete: bool
    can_delete_extra: bool
    min_num: int
    max_num: int
    absolute_max: int
    validate_min: bool
    validate_max: bool

    is_bound: bool
    prefix: str | None
    auto_id: str
    data: _DataT
    files: _FilesT
    initial: Sequence[Mapping[str, Any]] | None
    form_kwargs: dict[str, Any]
    error_class: type[ErrorList]
    deletion_widget: MediaDefiningClass
    ordering_widget: MediaDefiningClass
    default_error_messages: _ErrorMessagesDict
    template_name_div: str
    template_name_p: str
    template_name_table: str
    template_name_ul: str
    def __init__(
        self,
        data: _DataT | None = None,
        files: _FilesT | None = None,
        auto_id: str = "id_%s",
        prefix: str | None = None,
        initial: Sequence[Mapping[str, Any]] | None = None,
        error_class: type[ErrorList] = ...,
        form_kwargs: dict[str, Any] | None = None,
        error_messages: Mapping[str, str] | None = None,
        form_renderer: BaseRenderer = ...,
        renderer: BaseRenderer = ...,
    ) -> None: ...
    def __iter__(self) -> Iterator[_F]: ...
    def __getitem__(self, index: int) -> _F: ...
    def __len__(self) -> int: ...
    def __bool__(self) -> bool: ...
    @cached_property
    def management_form(self) -> ManagementForm: ...
    def total_form_count(self) -> int: ...
    def initial_form_count(self) -> int: ...
    @cached_property
    def forms(self) -> list[_F]: ...
    def get_form_kwargs(self, index: int | None) -> dict[str, Any]: ...
    @property
    def initial_forms(self) -> list[_F]: ...
    @property
    def extra_forms(self) -> list[_F]: ...
    @property
    def empty_form(self) -> _F: ...
    @property
    def cleaned_data(self) -> list[dict[str, Any]]: ...
    @property
    def deleted_forms(self) -> list[_F]: ...
    @property
    def ordered_forms(self) -> list[_F]: ...
    @classmethod
    def get_default_prefix(cls) -> str: ...
    @classmethod
    def get_deletion_widget(cls) -> type[Widget]: ...
    @classmethod
    def get_ordering_widget(cls) -> type[Widget]: ...
    def non_form_errors(self) -> ErrorList: ...
    @property
    def errors(self) -> list[ErrorList]: ...
    def total_error_count(self) -> int: ...
    def is_valid(self) -> bool: ...
    def full_clean(self) -> None: ...
    def clean(self) -> None: ...
    def has_changed(self) -> bool: ...
    def add_fields(self, form: _F, index: int | None) -> None: ...
    def add_prefix(self, index: int | str) -> str: ...
    def is_multipart(self) -> bool: ...
    @property
    def media(self) -> Media: ...
    @property
    def template_name(self) -> str: ...

def formset_factory(
    form: type[_F],
    formset: type[BaseFormSet[_F]] = ...,
    extra: int = 1,
    can_order: bool = False,
    can_delete: bool = False,
    max_num: int | None = None,
    validate_max: bool = False,
    min_num: int | None = None,
    validate_min: bool = False,
    absolute_max: int | None = None,
    can_delete_extra: bool = True,
) -> type[BaseFormSet[_F]]: ...
def all_valid(formsets: Sequence[BaseFormSet[_F]]) -> bool: ...
