from typing import dataclass_transform
from mod1 import field1
import mod2
from mod3 import *

@dataclass_transform(eq_default=False, order_default=True, field_specifiers=(field1, mod2.field2, field3)
def func()
    ...