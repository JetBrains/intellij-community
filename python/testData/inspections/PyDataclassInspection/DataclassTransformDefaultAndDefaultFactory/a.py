from typing import ClassVar

from decorator import my_field, my_dataclass


@my_dataclass()
class E1:
    a: int = my_field(default=1)
    b1: int = my_field(default_factory=int)
    b2: int = my_field(factory=int)
    c1: int = my_field<error descr="Cannot specify both 'default' and 'default_factory'">(default=1, default_factory=int)</error>
    c2: int = my_field<error descr="Cannot specify both 'default' and 'default_factory'">(default=1, factory=int)</error>
    d: ClassVar[int] = my_field(default_factory=<error descr="Field cannot have a default factory">int</error>)
