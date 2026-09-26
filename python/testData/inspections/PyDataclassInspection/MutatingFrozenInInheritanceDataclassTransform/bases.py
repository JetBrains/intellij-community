from decorator import my_dataclass, my_dataclass_frozen_default


@my_dataclass_frozen_default()
class BaseFrozenWithFrozenDefault:
    a: int = 1
