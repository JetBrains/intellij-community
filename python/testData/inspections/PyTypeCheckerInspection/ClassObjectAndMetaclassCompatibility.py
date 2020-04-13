class MetaClass(type):
    pass


class SubMetaClass(MetaClass):
    pass


class MetaClass2(type):
    pass


class MyClass(object):
    __metaclass__ = MetaClass
    pass


class MyClass2(object):
    __metaclass__ = SubMetaClass


def builder():
    return MetaClass()


Generated = builder()


class MyClass3(Generated):
    pass


class MyClass4:
    __metaclass__ = MetaClass2
    pass


class MyClass5:
    pass


def f(x):
    # type: (MetaClass) -> None
    pass


f(MyClass)
f(MyClass2)
f(Generated)
f(MyClass3)
f(<warning descr="Expected type 'MetaClass', got 'Type[MyClass4]' instead">MyClass4</warning>)
f(<warning descr="Expected type 'MetaClass', got 'Type[MyClass5]' instead">MyClass5</warning>)
