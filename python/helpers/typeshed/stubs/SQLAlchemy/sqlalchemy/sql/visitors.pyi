from typing import Any

class TraversibleType(type):
    def __init__(cls, clsname, bases, clsdict) -> None: ...

class Traversible:
    def __class_getitem__(cls, key): ...
    def get_children(self, omit_attrs=..., **kw): ...

class _InternalTraversalType(type):
    def __init__(cls, clsname, bases, clsdict) -> None: ...

class InternalTraversal:
    def dispatch(self, visit_symbol): ...
    def run_generated_dispatch(self, target, internal_dispatch, generate_dispatcher_name): ...
    def generate_dispatch(self, target_cls, internal_dispatch, generate_dispatcher_name): ...
    dp_has_cache_key: Any
    dp_has_cache_key_list: Any
    dp_clauseelement: Any
    dp_fromclause_canonical_column_collection: Any
    dp_clauseelement_tuples: Any
    dp_clauseelement_list: Any
    dp_clauseelement_tuple: Any
    dp_executable_options: Any
    dp_with_context_options: Any
    dp_fromclause_ordered_set: Any
    dp_string: Any
    dp_string_list: Any
    dp_anon_name: Any
    dp_boolean: Any
    dp_operator: Any
    dp_type: Any
    dp_plain_dict: Any
    dp_dialect_options: Any
    dp_string_clauseelement_dict: Any
    dp_string_multi_dict: Any
    dp_annotations_key: Any
    dp_plain_obj: Any
    dp_named_ddl_element: Any
    dp_prefix_sequence: Any
    dp_table_hint_list: Any
    dp_setup_join_tuple: Any
    dp_memoized_select_entities: Any
    dp_statement_hint_list: Any
    dp_unknown_structure: Any
    dp_dml_ordered_values: Any
    dp_dml_values: Any
    dp_dml_multi_values: Any
    dp_propagate_attrs: Any

class ExtendedInternalTraversal(InternalTraversal):
    dp_ignore: Any
    dp_inspectable: Any
    dp_multi: Any
    dp_multi_list: Any
    dp_has_cache_key_tuples: Any
    dp_inspectable_list: Any

class ExternalTraversal:
    __traverse_options__: Any
    def traverse_single(self, obj, **kw): ...
    def iterate(self, obj): ...
    def traverse(self, obj): ...
    @property
    def visitor_iterator(self) -> None: ...
    def chain(self, visitor): ...

class CloningExternalTraversal(ExternalTraversal):
    def copy_and_process(self, list_): ...
    def traverse(self, obj): ...

class ReplacingExternalTraversal(CloningExternalTraversal):
    def replace(self, elem) -> None: ...
    def traverse(self, obj): ...

Visitable = Traversible
VisitableType = TraversibleType
ClauseVisitor = ExternalTraversal
CloningVisitor = CloningExternalTraversal
ReplacingCloningVisitor = ReplacingExternalTraversal

def iterate(obj, opts=...) -> None: ...
def traverse_using(iterator, obj, visitors): ...
def traverse(obj, opts, visitors): ...
def cloned_traverse(obj, opts, visitors): ...
def replacement_traverse(obj, opts, replace): ...
