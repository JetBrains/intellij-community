from bases import BaseFrozenExplicit, BaseNonFrozenImplicit, BaseFrozenWithFrozenDefault
from decorator import my_dataclass, my_dataclass_frozen_default


@my_dataclass()
class <error descr="Frozen dataclasses can not inherit non-frozen one and vice versa">B1</error>(BaseFrozenExplicit):
    b: str = "2"


@my_dataclass(<error descr="Frozen dataclasses can not inherit non-frozen one and vice versa">frozen=True</error>)
class B2(BaseNonFrozenImplicit):
    b: str = "2"


@my_dataclass()
class <error descr="Frozen dataclasses can not inherit non-frozen one and vice versa">B1</error>(BaseFrozenWithFrozenDefault):
    b: str = "2"


@my_dataclass_frozen_default()
class <error descr="Frozen dataclasses can not inherit non-frozen one and vice versa">B2</error>(BaseNonFrozenImplicit):
    b: str = "2"
