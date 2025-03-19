from decorator import my_dataclass, my_dataclass_frozen_default


@my_dataclass(frozen=True)
class BaseFrozenExplicit:
    a: int = 1


@my_dataclass()
class BaseNonFrozenImplicit:
    a: int = 1


@my_dataclass_frozen_default()
class BaseFrozenWithFrozenDefault:
    a: int = 1
