from typing import Any, TypeVar

from django.db.models import Model
from django.forms.models import BaseModelFormSet, ModelForm

_M = TypeVar("_M", bound=Model)
_ModelFormT = TypeVar("_ModelFormT", bound=ModelForm)

class BaseGenericInlineFormSet(BaseModelFormSet[_M, _ModelFormT]):
    instance: Any
    rel_name: Any
    save_as_new: Any
    def __init__(
        self,
        data: Any | None = ...,
        files: Any | None = ...,
        instance: Any | None = ...,
        save_as_new: bool = ...,
        prefix: Any | None = ...,
        queryset: Any | None = ...,
        **kwargs: Any,
    ) -> None: ...
    def initial_form_count(self) -> int: ...
    @classmethod
    def get_default_prefix(cls) -> str: ...
    def save_new(self, form: Any, commit: bool = ...) -> _M: ...

def generic_inlineformset_factory(
    model: type[_M],
    form: type[_ModelFormT] = ...,
    formset: Any = ...,
    ct_field: str = ...,
    fk_field: str = ...,
    fields: Any | None = ...,
    exclude: Any | None = ...,
    extra: int = ...,
    can_order: bool = ...,
    can_delete: bool = ...,
    max_num: Any | None = ...,
    formfield_callback: Any | None = ...,
    validate_max: bool = ...,
    for_concrete_model: bool = ...,
    min_num: Any | None = ...,
    validate_min: bool = ...,
    absolute_max: int | None = ...,
    can_delete_extra: bool = ...,
) -> type[BaseGenericInlineFormSet[_M, _ModelFormT]]: ...
