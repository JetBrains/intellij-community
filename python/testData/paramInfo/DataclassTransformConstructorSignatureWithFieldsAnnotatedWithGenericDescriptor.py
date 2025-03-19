from typing import dataclass_transform, TypeVar, Generic

T = TypeVar("T")

@dataclass_transform()
def deco(cls):
  ...

class MyDescriptor(Generic[T]):
  def __set__(self, obj: object, value: T) -> None:
      ...

@deco
class MyClass:
   id: MyDescriptor[int]
   name: MyDescriptor[str]
   year: MyDescriptor[int]
   new: MyDescriptor[bool]

MyClass(<arg1>)