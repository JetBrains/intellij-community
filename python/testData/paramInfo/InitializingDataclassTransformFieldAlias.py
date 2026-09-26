from typing import Callable, dataclass_transform

def field(**kwargs):
    ...


@dataclass_transform(field_specifiers=(field,))
def my_dataclass(**kwargs) -> Callable[[type], type]:
    ...

@my_dataclass()
class Dataclass:
    x: int = field(alias="foo")
    y: int = field(alias="bar")

Dataclass(<arg1>)