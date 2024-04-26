from _typeshed import Incomplete

def paged_search_generator(
    connection,
    search_base,
    search_filter,
    search_scope="SUBTREE",
    dereference_aliases="ALWAYS",
    attributes: Incomplete | None = None,
    size_limit: int = 0,
    time_limit: int = 0,
    types_only: bool = False,
    get_operational_attributes: bool = False,
    controls: Incomplete | None = None,
    paged_size: int = 100,
    paged_criticality: bool = False,
) -> None: ...
def paged_search_accumulator(
    connection,
    search_base,
    search_filter,
    search_scope="SUBTREE",
    dereference_aliases="ALWAYS",
    attributes: Incomplete | None = None,
    size_limit: int = 0,
    time_limit: int = 0,
    types_only: bool = False,
    get_operational_attributes: bool = False,
    controls: Incomplete | None = None,
    paged_size: int = 100,
    paged_criticality: bool = False,
): ...
