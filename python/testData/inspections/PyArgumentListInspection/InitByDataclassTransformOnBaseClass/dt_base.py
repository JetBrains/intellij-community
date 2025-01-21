from typing import dataclass_transform

import dt_field


@dataclass_transform(field_specifiers=(dt_field.DataclassField,))
class DataclassBase:
    def __init_subclass__(cls, **kwargs):
        import dataclasses
        cls.__dir__ = dataclasses.dataclass(**kwargs)(cls).__dir__
