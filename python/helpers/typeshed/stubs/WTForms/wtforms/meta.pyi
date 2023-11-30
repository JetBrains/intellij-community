from _typeshed import SupportsItems
from collections.abc import Collection, Iterator, MutableMapping
from typing import Any, Protocol, TypeVar, overload
from typing_extensions import Literal, TypeAlias

from markupsafe import Markup
from wtforms.fields.core import Field, UnboundField
from wtforms.form import BaseForm

_FieldT = TypeVar("_FieldT", bound=Field)

class _SupportsGettextAndNgettext(Protocol):
    def gettext(self, __string: str) -> str: ...
    def ngettext(self, __singular: str, __plural: str, __n: int) -> str: ...

# these are the methods WTForms depends on, the dict can either provide
# a getlist or getall, if it only provies getall, it will wrapped, to
# provide getlist instead
class _MultiDictLikeBase(Protocol):
    def __iter__(self) -> Iterator[str]: ...
    def __len__(self) -> int: ...
    def __contains__(self, __key: Any) -> bool: ...

# since how file uploads are represented in formdata is implementation-specific
# we have to be generous in what we accept in the return of getlist/getall
# we can make this generic if we ever want to be more specific
class _MultiDictLikeWithGetlist(_MultiDictLikeBase, Protocol):
    def getlist(self, __key: str) -> list[Any]: ...

class _MultiDictLikeWithGetall(_MultiDictLikeBase, Protocol):
    def getall(self, __key: str) -> list[Any]: ...

_MultiDictLike: TypeAlias = _MultiDictLikeWithGetall | _MultiDictLikeWithGetlist

class DefaultMeta:
    def bind_field(self, form: BaseForm, unbound_field: UnboundField[_FieldT], options: MutableMapping[str, Any]) -> _FieldT: ...
    @overload
    def wrap_formdata(self, form: BaseForm, formdata: None) -> None: ...
    @overload
    def wrap_formdata(self, form: BaseForm, formdata: _MultiDictLike) -> _MultiDictLikeWithGetlist: ...
    def render_field(self, field: Field, render_kw: SupportsItems[str, Any]) -> Markup: ...
    csrf: bool
    csrf_field_name: str
    csrf_secret: Any | None
    csrf_context: Any | None
    csrf_class: type[Any] | None
    def build_csrf(self, form: BaseForm) -> Any: ...
    locales: Literal[False] | Collection[str]
    cache_translations: bool
    translations_cache: dict[str, _SupportsGettextAndNgettext]
    def get_translations(self, form: BaseForm) -> _SupportsGettextAndNgettext: ...
    def update_values(self, values: SupportsItems[str, Any]) -> None: ...
    # since meta can be extended with arbitary data we add a __getattr__
    # method that returns Any
    def __getattr__(self, name: str) -> Any: ...
