from typing import Protocol

# classes having type parameter list implicitly inherit from typing.Generic
class Clazz[T](Protocol): ...