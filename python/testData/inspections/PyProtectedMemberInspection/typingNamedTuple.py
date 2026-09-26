from typing import NamedTuple


class Cat1(NamedTuple):
    name: str
    age: int
c1 = Cat1("name", 5)
print(c1._make)
print(c1._asdict)
print(c1._replace)
print(c1._fields)
print(c1._field_defaults)


Cat2 = NamedTuple("Cat2", name=str, age=int)
c2 = Cat2("name", 5)
print(c2._make)
print(c2._asdict)
print(c2._replace)
print(c2._fields)
print(c2._field_defaults)