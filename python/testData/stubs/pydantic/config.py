from typing import Any, Literal, Optional, TypedDict


class ConfigDict(TypedDict, total=False):
    populate_by_name: bool
    extra: Literal['allow', 'ignore', 'forbid']
    str_max_length: Optional[int]
    strict: bool
    frozen: bool
    validate_default: bool
    arbitrary_types_allowed: bool