from typing import TypedDict


class ClosedDict(TypedDict, closed=True, extra_items=bool):
    field: str
