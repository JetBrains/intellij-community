from collections.abc import Callable, Iterable, Iterator
from typing import Any, Generic, TypeVar

from wtforms.fields.core import Field, UnboundField, _FormT, _Validator, _Widget
from wtforms.form import BaseForm
from wtforms.meta import DefaultMeta, _SupportsGettextAndNgettext

_BoundFieldT = TypeVar("_BoundFieldT", bound=Field)

class FieldList(Field, Generic[_BoundFieldT]):
    unbound_field: UnboundField[_BoundFieldT]
    min_entries: int
    max_entries: int | None
    last_index: int
    entries: list[_BoundFieldT]
    object_data: Iterable[Any]
    def __init__(
        self: FieldList[_BoundFieldT],
        # because of our workaround we need to accept Field as well
        unbound_field: UnboundField[_BoundFieldT] | _BoundFieldT,
        label: str | None = None,
        validators: tuple[_Validator[_FormT, _BoundFieldT], ...] | list[Any] | None = None,
        min_entries: int = 0,
        max_entries: int | None = None,
        separator: str = "-",
        default: Iterable[Any] | Callable[[], Iterable[Any]] = (),
        *,
        description: str = "",
        id: str | None = None,
        widget: _Widget[FieldList[Any]] | None = None,
        render_kw: dict[str, Any] | None = None,
        name: str | None = None,
        _form: BaseForm | None = None,
        _prefix: str = "",
        _translations: _SupportsGettextAndNgettext | None = None,
        _meta: DefaultMeta | None = None,
    ) -> None: ...
    def append_entry(self, data: Any = ...) -> _BoundFieldT: ...
    def pop_entry(self) -> _BoundFieldT: ...
    def __iter__(self) -> Iterator[_BoundFieldT]: ...
    def __len__(self) -> int: ...
    def __getitem__(self, index: int) -> _BoundFieldT: ...
    @property
    def data(self) -> list[Any]: ...
