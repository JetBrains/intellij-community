from typing import Literal

from m import foo_bar, MyEnum

var: [Literal[MyEnum.A]] = foo_bar()
