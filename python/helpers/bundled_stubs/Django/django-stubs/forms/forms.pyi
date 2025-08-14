from collections.abc import Iterable, Iterator, Mapping
from typing import Any, ClassVar

from django.core.exceptions import ValidationError
from django.forms.boundfield import BoundField
from django.forms.fields import Field
from django.forms.renderers import BaseRenderer
from django.forms.utils import ErrorDict, ErrorList, RenderableFormMixin, _DataT, _FilesT
from django.forms.widgets import Media, MediaDefiningClass
from django.utils.functional import _StrOrPromise, cached_property

class DeclarativeFieldsMetaclass(MediaDefiningClass): ...

class BaseForm(RenderableFormMixin):
    default_renderer: BaseRenderer | type[BaseRenderer] | None
    field_order: Iterable[str] | None
    use_required_attribute: bool
    is_bound: bool
    data: _DataT
    files: _FilesT
    auto_id: bool | str
    initial: Mapping[str, Any]
    error_class: type[ErrorList]
    prefix: str | None
    label_suffix: str
    empty_permitted: bool
    fields: dict[str, Field]
    renderer: BaseRenderer
    cleaned_data: dict[str, Any]
    template_name_div: str
    template_name_p: str
    template_name_table: str
    template_name_ul: str
    template_name_label: str
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
        field_order: Iterable[str] | None = None,
        use_required_attribute: bool | None = None,
        renderer: BaseRenderer | None = None,
    ) -> None: ...
    def order_fields(self, field_order: Iterable[str] | None) -> None: ...
    def __iter__(self) -> Iterator[BoundField]: ...
    def __getitem__(self, name: str) -> BoundField: ...
    @property
    def errors(self) -> ErrorDict: ...
    def is_valid(self) -> bool: ...
    def add_prefix(self, field_name: str) -> str: ...
    def add_initial_prefix(self, field_name: str) -> str: ...
    @property
    def template_name(self) -> str: ...
    def non_field_errors(self) -> ErrorList: ...
    def add_error(self, field: str | None, error: ValidationError | _StrOrPromise) -> None: ...
    def has_error(self, field: str | None, code: str | None = None) -> bool: ...
    def full_clean(self) -> None: ...
    def clean(self) -> dict[str, Any] | None: ...
    def has_changed(self) -> bool: ...
    @cached_property
    def changed_data(self) -> list[str]: ...
    @property
    def media(self) -> Media: ...
    def is_multipart(self) -> bool: ...
    def hidden_fields(self) -> list[BoundField]: ...
    def visible_fields(self) -> list[BoundField]: ...
    def get_initial_for_field(self, field: Field, field_name: str) -> Any: ...

class Form(BaseForm, metaclass=DeclarativeFieldsMetaclass):
    base_fields: ClassVar[dict[str, Field]]
    declared_fields: ClassVar[dict[str, Field]]
