class MetaClass(type):
    pass


class SubMetaClass(MetaClass):
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


def f(x):
    # type: (MetaClass) -> None
    pass


f(MyClass)
f(MyClass2)
f(Generated)
f(MyClass3)
