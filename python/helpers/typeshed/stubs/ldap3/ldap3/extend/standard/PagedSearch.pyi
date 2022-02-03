from typing import Any

def paged_search_generator(
    connection,
    search_base,
    search_filter,
    search_scope=...,
    dereference_aliases=...,
    attributes: Any | None = ...,
    size_limit: int = ...,
    time_limit: int = ...,
    types_only: bool = ...,
    get_operational_attributes: bool = ...,
    controls: Any | None = ...,
    paged_size: int = ...,
    paged_criticality: bool = ...,
) -> None: ...
def paged_search_accumulator(
    connection,
    search_base,
    search_filter,
    search_scope=...,
    dereference_aliases=...,
    attributes: Any | None = ...,
    size_limit: int = ...,
    time_limit: int = ...,
    types_only: bool = ...,
    get_operational_attributes: bool = ...,
    controls: Any | None = ...,
    paged_size: int = ...,
    paged_criticality: bool = ...,
): ...
