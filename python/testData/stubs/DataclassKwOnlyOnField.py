from dataclasses import dataclass, field

@dataclass
class Foo:
    bar1: str = field()
    bar2: str = field(kw_only=True)
    bar3: int = field(kw_only=False)