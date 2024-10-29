from typing import dataclass_transform


@dataclass_transform()
class DataclassBase:
    def __init_subclass__(cls, **kwargs):
        import dataclasses
        cls.__dir__ = dataclasses.dataclass(**kwargs)(cls).__dir__
