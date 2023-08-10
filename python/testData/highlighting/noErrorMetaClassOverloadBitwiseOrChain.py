class MyMeta(type):
    def __or__(self, other):
        return other

class Foo(metaclass=MyMeta):
    ...
class Bar(metaclass=MyMeta):
    ...
class Baz(metaclass=MyMeta):
    ...

print(Foo | Bar | Baz)
