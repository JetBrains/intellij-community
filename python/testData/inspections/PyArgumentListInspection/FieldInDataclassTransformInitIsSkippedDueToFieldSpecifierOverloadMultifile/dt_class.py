from typing import Callable, dataclass_transform

from dt_field import field1

@dataclass_transform(kw_only_default=True, field_specifiers=(field1,))
def create_model[T](*, init: bool = True) -> Callable[[type[T]], type[T]]:
    ...


@create_model()
class CustomerModel1:
    id: int = field1(resolver=lambda: 0)
    name: str = field1(default="Voldemort")