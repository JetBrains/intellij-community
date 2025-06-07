from typing import dataclass_transform

import dt_field


@dataclass_transform(field_specifiers=(dt_field.DataclassField,))
class DataclassMeta(type):
    def __new__(
            cls,
            name,
            bases,
            namespace,
            **kwargs,
    ):
        import dataclasses
        return dataclasses.dataclass(**kwargs)(super().__new__(cls, name, bases, namespace))  # type: ignore



