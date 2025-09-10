from collections import namedtuple


class Cat1(namedtuple("Cat", "name age")):
    pass
c1 = Cat1("name", 5)
print(c1._make)
print(c1._asdict)
print(c1._replace)
print(c1._fields)
print(c1._field_defaults)


Cat2 = namedtuple("Cat2", "name age")
c2 = Cat2("name", 5)
print(c2._make)
print(c2._asdict)
print(c2._replace)
print(c2._fields)
print(c2._field_defaults)