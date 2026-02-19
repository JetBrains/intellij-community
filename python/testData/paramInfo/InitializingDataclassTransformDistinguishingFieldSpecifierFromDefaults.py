from typing import Callable, dataclass_transform

def field(**kwargs):
    ...

def not_field(**kwargs):
    ...


@dataclass_transform(field_specifiers=(field,))
def my_dataclass(**kwargs) -> Callable[[type], type]:
    ...



@my_dataclass()
class Dataclass:
    field1: int = field()
    field2: int = field(default=42)
    field3: int = not_field()
    field4: int = not_field(default=42)


Dataclass(<arg1>)