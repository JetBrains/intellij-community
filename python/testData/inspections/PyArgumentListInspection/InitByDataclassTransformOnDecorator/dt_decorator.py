from typing import dataclass_transform, Callable

import dt_field


@dataclass_transform(field_specifiers=(dt_field.DataclassField,))
def my_dt_decorator(**kwargs) -> Callable[[type], type]:
    import dataclasses
    return dataclasses.dataclass(**kwargs)  # type: ignore
