from decorator import my_dataclass, my_field
    

@my_dataclass()
class Person:
    name: str = my_field(alias="full_name")
    age: int