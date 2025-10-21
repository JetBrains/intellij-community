from typing import Any, NamedTuple

class Operation(NamedTuple):
    request: Any
    result: Any
    response: Any

class Cursor:
    connection: Any
    get_operational_attributes: Any
    definition: Any
    attributes: Any
    controls: Any
    execution_time: Any
    entries: Any
    schema: Any
    def __init__(
        self,
        connection,
        object_def,
        get_operational_attributes: bool = False,
        attributes=None,
        controls=None,
        auxiliary_class=None,
    ) -> None: ...
    def __iter__(self): ...
    def __getitem__(self, item): ...
    def __len__(self) -> int: ...
    def __bool__(self) -> bool: ...
    def match_dn(self, dn): ...
    def match(self, attributes, value): ...
    def remove(self, entry) -> None: ...
    @property
    def operations(self): ...
    @property
    def errors(self): ...
    @property
    def failed(self): ...

class Reader(Cursor):
    entry_class: Any
    attribute_class: Any
    entry_initial_status: Any
    sub_tree: Any
    base: Any
    dereference_aliases: Any
    validated_query: Any
    query_filter: Any
    def __init__(
        self,
        connection,
        object_def,
        base,
        query: str = "",
        components_in_and: bool = True,
        sub_tree: bool = True,
        get_operational_attributes: bool = False,
        attributes=None,
        controls=None,
        auxiliary_class=None,
    ) -> None: ...
    @property
    def query(self): ...
    @query.setter
    def query(self, value) -> None: ...
    @property
    def components_in_and(self): ...
    @components_in_and.setter
    def components_in_and(self, value) -> None: ...
    def clear(self) -> None: ...
    execution_time: Any
    entries: Any
    def reset(self) -> None: ...
    def search(self, attributes=None): ...
    def search_object(self, entry_dn=None, attributes=None): ...
    def search_level(self, attributes=None): ...
    def search_subtree(self, attributes=None): ...
    def search_paged(self, paged_size, paged_criticality: bool = True, generator: bool = True, attributes=None): ...

class Writer(Cursor):
    entry_class: Any
    attribute_class: Any
    entry_initial_status: Any
    @staticmethod
    def from_cursor(cursor, connection=None, object_def=None, custom_validator=None): ...
    @staticmethod
    def from_response(connection, object_def, response=None): ...
    dereference_aliases: Any
    def __init__(
        self,
        connection,
        object_def,
        get_operational_attributes: bool = False,
        attributes=None,
        controls=None,
        auxiliary_class=None,
    ) -> None: ...
    execution_time: Any
    def commit(self, refresh: bool = True): ...
    def discard(self) -> None: ...
    def new(self, dn): ...
    def refresh_entry(self, entry, tries: int = 4, seconds: int = 2): ...
