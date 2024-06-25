from typing import dataclass_transform, Callable


@dataclass_transform()
def my_dt_decorator(**kwargs) -> Callable[[type], type]:
    import dataclasses
    return dataclasses.dataclass(**kwargs)  # type: ignore
