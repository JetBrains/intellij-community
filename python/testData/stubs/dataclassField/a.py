import dataclasses
import dataclasses as dc
from dataclasses import field
from dataclasses import field as F
from b import INIT_3


INIT_0 = False
INIT_1 = False
INIT_2 = INIT_1


@dataclasses.dataclass
class A:
    a: int = dataclasses.field(default=1)
    b: int = dc.field(default_factory=int)
    c: int = field(init=False)
    d: int = F(init=True)
    e: int = field(init=INIT_0)
    f: int = field(init=INIT_2)
    g: int = field(init=INIT_3)
    h: int = field()
    i: int = field(default=dataclasses.MISSING, default_factory=dataclasses.MISSING)