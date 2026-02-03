class Meta(type):
    def __new__(cls, name, bases, attrs):
        foo = "abc"


class A(metaclass=Meta):
    pass


print(A().<warning descr="Unresolved attribute reference 'bar' for class 'A'">bar</warning>)
