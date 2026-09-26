from typing import TypedDict

td = TypedDict(
    "name",
    {
        "field": str,
    },
    closed=True,
    extra_items=bool,
)