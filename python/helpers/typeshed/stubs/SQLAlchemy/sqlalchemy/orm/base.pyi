from typing import Any

from ..util import memoized_property

PASSIVE_NO_RESULT: Any
PASSIVE_CLASS_MISMATCH: Any
ATTR_WAS_SET: Any
ATTR_EMPTY: Any
NO_VALUE: Any
NEVER_SET: Any
NO_CHANGE: Any
CALLABLES_OK: Any
SQL_OK: Any
RELATED_OBJECT_OK: Any
INIT_OK: Any
NON_PERSISTENT_OK: Any
LOAD_AGAINST_COMMITTED: Any
NO_AUTOFLUSH: Any
NO_RAISE: Any
DEFERRED_HISTORY_LOAD: Any
PASSIVE_OFF: Any
PASSIVE_RETURN_NO_VALUE: Any
PASSIVE_NO_INITIALIZE: Any
PASSIVE_NO_FETCH: Any
PASSIVE_NO_FETCH_RELATED: Any
PASSIVE_ONLY_PERSISTENT: Any
DEFAULT_MANAGER_ATTR: str
DEFAULT_STATE_ATTR: str
EXT_CONTINUE: Any
EXT_STOP: Any
EXT_SKIP: Any
ONETOMANY: Any
MANYTOONE: Any
MANYTOMANY: Any
NOT_EXTENSION: Any

_never_set: frozenset[Any]
_none_set: frozenset[Any]

def manager_of_class(cls): ...

instance_state: Any
instance_dict: Any

def instance_str(instance): ...
def state_str(state): ...
def state_class_str(state): ...
def attribute_str(instance, attribute): ...
def state_attribute_str(state, attribute): ...
def object_mapper(instance): ...
def object_state(instance): ...
def _class_to_mapper(class_or_mapper): ...
def _mapper_or_none(entity): ...
def _is_mapped_class(entity): ...

_state_mapper: Any

def class_mapper(class_, configure: bool = ...): ...

class InspectionAttr:
    is_selectable: bool
    is_aliased_class: bool
    is_instance: bool
    is_mapper: bool
    is_bundle: bool
    is_property: bool
    is_attribute: bool
    is_clause_element: bool
    extension_type: Any

class InspectionAttrInfo(InspectionAttr):
    @memoized_property
    def info(self): ...

class _MappedAttribute: ...
