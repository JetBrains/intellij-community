import dataclasses


@dataclasses.dataclass
class A1:
    bar1: int = 1
    _: dataclasses.KW_ONLY
    bar2: int
