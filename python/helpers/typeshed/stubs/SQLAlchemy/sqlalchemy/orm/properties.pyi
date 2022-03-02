from typing import Any

from .. import util
from .descriptor_props import (
    CompositeProperty as CompositeProperty,
    ConcreteInheritedProperty as ConcreteInheritedProperty,
    SynonymProperty as SynonymProperty,
)
from .interfaces import PropComparator, StrategizedProperty
from .relationships import RelationshipProperty as RelationshipProperty

__all__ = ["ColumnProperty", "CompositeProperty", "ConcreteInheritedProperty", "RelationshipProperty", "SynonymProperty"]

class ColumnProperty(StrategizedProperty):
    logger: Any
    strategy_wildcard_key: str
    inherit_cache: bool
    columns: Any
    group: Any
    deferred: Any
    raiseload: Any
    instrument: Any
    comparator_factory: Any
    descriptor: Any
    active_history: Any
    expire_on_flush: Any
    info: Any
    doc: Any
    strategy_key: Any
    def __init__(self, *columns, **kwargs) -> None: ...
    def __clause_element__(self): ...
    @property
    def expression(self): ...
    def instrument_class(self, mapper) -> None: ...
    def do_init(self) -> None: ...
    def copy(self): ...
    def merge(
        self, session, source_state, source_dict, dest_state, dest_dict, load, _recursive, _resolve_conflict_map
    ) -> None: ...

    class Comparator(util.MemoizedSlots, PropComparator):
        expressions: Any
        def _memoized_method___clause_element__(self): ...
        def operate(self, op, *other, **kwargs): ...
        def reverse_operate(self, op, other, **kwargs): ...
