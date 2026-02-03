from typing import Callable, dataclass_transform

def field_init_default_false(init: bool = False):
    ...

def field(**kwargs):
    ...


@dataclass_transform(field_specifiers=(field_init_default_false, field))
def my_dataclass(**kwargs) -> Callable[[type], type]:
    ...

@my_dataclass()
class Dataclass:
    not_init_spec_default: int = field_init_default_false()
    not_init_spec_param: int = field(init=False)
    init_spec_param: int = field(init=True)
    init_inferred: int = field()

Dataclass(<arg1>)