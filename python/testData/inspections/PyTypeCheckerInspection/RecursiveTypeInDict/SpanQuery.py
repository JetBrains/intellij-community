from typing_extensions import TypedDict

class SpanQuery(TypedDict, total=False):
    """A serializable query for filtering SpanNodes based on various conditions.

    All fields are optional and combined with AND logic by default.
    """

    # These fields are ordered to match the implementation of SpanNode.matches_query for easy review.
    # * Individual span conditions come first because these are generally the cheapest to evaluate
    # * Logical combinations come next because they may just be combinations of individual span conditions
    # * Related-span conditions come last because they may require the most work to evaluate

    # Individual span conditions
    ## Name conditions
    name_equals: str
    name_contains: str
    name_matches_regex: str  # regex pattern

    ## Attribute conditions
    has_attributes: dict[str, Any]
    has_attribute_keys: list[str]

    ## Timing conditions
    min_duration: timedelta | float
    max_duration: timedelta | float

    # Logical combinations of conditions
    not_: SpanQuery
    and_: list[SpanQuery]
    or_: list[SpanQuery]

    # Child conditions
    min_child_count: int
    max_child_count: int
    some_child_has: SpanQuery
    all_children_have: SpanQuery
    no_child_has: SpanQuery

    # Recursive conditions
    stop_recursing_when: SpanQuery
    """If present, stop recursing through ancestors or descendants at nodes that match this condition."""

    ## Descendant conditions
    min_descendant_count: int
    max_descendant_count: int
    some_descendant_has: SpanQuery
    all_descendants_have: SpanQuery
    no_descendant_has: SpanQuery

    ## Ancestor conditions
    min_depth: int  # depth is equivalent to ancestor count; roots have depth 0
    max_depth: int
    some_ancestor_has: SpanQuery
    all_ancestors_have: SpanQuery
    no_ancestor_has: SpanQuery