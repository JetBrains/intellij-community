from dataclasses import *


@dataclass(eq=True)
class Foo:
    foo: int


print(Foo(foo=42))
#          <ref>