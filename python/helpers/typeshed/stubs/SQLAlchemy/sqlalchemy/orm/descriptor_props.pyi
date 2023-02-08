from typing import Any, Generic, TypeVar

from ..sql.operators import ColumnOperators
from ..util import memoized_property
from . import util as orm_util
from .interfaces import MapperProperty, PropComparator

_T = TypeVar("_T")

class DescriptorProperty(MapperProperty):
    doc: Any
    uses_objects: bool
    key: Any
    descriptor: Any
    def instrument_class(self, mapper): ...

class CompositeProperty(DescriptorProperty):
    attrs: Any
    composite_class: Any
    active_history: Any
    deferred: Any
    group: Any
    comparator_factory: Any
    info: Any
    def __init__(self, class_, *attrs, **kwargs) -> None: ...
    def instrument_class(self, mapper) -> None: ...
    def do_init(self) -> None: ...
    @memoized_property
    def props(self): ...
    @property
    def columns(self): ...
    def get_history(self, state, dict_, passive=...): ...

    class CompositeBundle(orm_util.Bundle):
        property: Any
        def __init__(self, property_, expr) -> None: ...
        def create_row_processor(self, query, procs, labels): ...

    class Comparator(PropComparator[_T], Generic[_T]):
        __hash__: Any
        @memoized_property
        def clauses(self): ...
        def __clause_element__(self): ...
        @memoized_property
        def expression(self): ...
        def __eq__(self, other) -> ColumnOperators[_T]: ...  # type: ignore[override]
        def __ne__(self, other) -> ColumnOperators[_T]: ...  # type: ignore[override]

class ConcreteInheritedProperty(DescriptorProperty):
    descriptor: Any
    def __init__(self): ...

class SynonymProperty(DescriptorProperty):
    name: Any
    map_column: Any
    descriptor: Any
    comparator_factory: Any
    doc: Any
    info: Any
    def __init__(
        self,
        name,
        map_column: Any | None = ...,
        descriptor: Any | None = ...,
        comparator_factory: Any | None = ...,
        doc: Any | None = ...,
        info: Any | None = ...,
    ) -> None: ...
    @property
    def uses_objects(self): ...
    def get_history(self, *arg, **kw): ...
    parent: Any
    def set_parent(self, parent, init) -> None: ...
