from typing import Literal
from m import SimpleEnum, SuperEnum

p: Literal[SuperEnum.PINK] = SuperEnum.PINK
q: Literal[SimpleEnum.FOO] = <warning descr="Expected type 'Literal[SimpleEnum.FOO]', got 'Literal[SuperEnum.PINK]' instead">SuperEnum.PINK</warning>