from typing import Optional, Any, overload, Union

class MyDescriptor[T]:
    @overload
    def __get__(self, instance: None, owner: Any) -> T: # access via class
        ...
    @overload
    def __get__(self, instance: object, owner: Any) -> str: # access via instance
        ...
    def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
        ...

class Test():
    member: MyDescriptor[int]