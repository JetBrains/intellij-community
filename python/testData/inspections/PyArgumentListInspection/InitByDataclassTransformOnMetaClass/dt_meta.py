from typing import dataclass_transform


@dataclass_transform()
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



